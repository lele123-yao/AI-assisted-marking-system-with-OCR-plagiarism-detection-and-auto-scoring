package com.marking.aimarkingsystem.paper.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.util.RedisUtil;
import com.marking.aimarkingsystem.paper.dto.PaperParseResult;
import com.marking.aimarkingsystem.paper.dto.PaperParseTask;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 试卷解析任务：提交即返回 taskId，OCR 在后台执行，前端轮询进度。
 *
 * 三个关键设计：
 * 1. 异步化：请求线程只做"上传对象存储 + PDF 取文本"这类秒级操作，
 *    最坏分钟级的 OCR 不再占用 HTTP 连接，网关/手机浏览器不会先超时；
 * 2. 单线程消费：本地 OCR 服务的识别引擎是全局共享的，并发调用会互相干扰，因此串行执行；
 * 3. 防重复提交：同一用户已有进行中的任务时直接返回该任务，避免连点重复上传、重复扣资源。
 *
 * 状态存储：内存 + Redis 双写。Redis 可用时任务状态可跨实例查询、30 分钟自动过期；
 * Redis 不可用时退化为单实例内存状态，不影响主流程。
 */
@Slf4j
@Service
public class PaperParseTaskService {

    private static final String TASK_KEY_PREFIX = "paper:parse:task:";
    private static final String RUNNING_KEY_PREFIX = "paper:parse:running:";

    /** 任务状态保留 30 分钟，够前端轮询与用户回看 */
    private static final long TASK_TTL_MINUTES = 30;

    private final PaperParseService paperParseService;
    private final RedisUtil redisUtil;

    /** 内存副本：Redis 未启动时任务仍可查询 */
    private final ConcurrentHashMap<String, String> localTasks = new ConcurrentHashMap<>();
    /** 每个用户当前进行中的任务，用于拦截连点 */
    private final ConcurrentHashMap<String, String> localRunning = new ConcurrentHashMap<>();

    /** 单线程：OCR 服务共享识别引擎，串行调用才不会互相踩 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "paper-parse-task");
        thread.setDaemon(true);
        return thread;
    });

    public PaperParseTaskService(PaperParseService paperParseService, RedisUtil redisUtil) {
        this.paperParseService = paperParseService;
        this.redisUtil = redisUtil;
    }

    /** 提交解析任务：上传素材后立即返回，不等待 OCR。 */
    public PaperParseTask submit(List<MultipartFile> files, String username) {
        String owner = username == null ? "anonymous" : username;

        String runningTaskId = findRunningTask(owner);
        if (runningTaskId != null) {
            PaperParseTask running = loadTask(runningTaskId);
            if (running != null && isRunning(running.getStatus())) {
                running.setMessage("已有解析任务在执行，已返回该任务进度");
                return running;
            }
            clearRunning(owner);
        }

        String taskId = IdUtil.fastSimpleUUID();
        String existingTaskId = localRunning.putIfAbsent(owner, taskId);
        if (existingTaskId != null) {
            // 并发提交时另一个请求已抢到执行权，返回它的任务，不重复上传
            PaperParseTask running = loadTask(existingTaskId);
            if (running != null && isRunning(running.getStatus())) {
                running.setMessage("已有解析任务在执行，已返回该任务进度");
                return running;
            }
            localRunning.put(owner, taskId);
        }

        long now = System.currentTimeMillis();
        PaperParseTask task = new PaperParseTask();
        task.setTaskId(taskId);
        task.setStatus("PENDING");
        task.setMessage("正在上传素材");
        task.setOwner(owner);
        task.setTotal(0);
        task.setDone(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        // 先落任务再上传：上传期间用户连点会命中这个任务，而不是再传一遍文件
        saveTask(task);
        redisUtil.setIfAbsent(RUNNING_KEY_PREFIX + owner, taskId, TASK_TTL_MINUTES, TimeUnit.MINUTES);

        PaperParseService.PreparedParse prepared;
        try {
            prepared = paperParseService.prepare(files);
        } catch (RuntimeException e) {
            // 校验/上传失败：任务标记失败并释放占位，避免用户之后一直被"已有任务"挡住
            task.setStatus("FAILED");
            task.setMessage("上传失败：" + e.getMessage());
            task.setUpdatedAt(System.currentTimeMillis());
            saveTask(task);
            clearRunning(owner);
            throw e;
        }

        task.setMessage("素材已上传，等待后台识别");
        task.setTotal(prepared.images().size());
        task.setUpdatedAt(System.currentTimeMillis());
        saveTask(task);

        executor.submit(() -> run(taskId, prepared, owner));
        return task;
    }

    /** 查询任务：只允许提交者本人查看。 */
    public PaperParseTask getTask(String taskId, String username) {
        PaperParseTask task = loadTask(taskId);
        if (task == null) {
            throw new BusinessException("解析任务不存在或已过期，请重新上传");
        }
        String owner = username == null ? "anonymous" : username;
        if (!Objects.equals(task.getOwner(), owner)) {
            throw new BusinessException("无权查看该解析任务");
        }
        return task;
    }

    private void run(String taskId, PaperParseService.PreparedParse prepared, String owner) {
        try {
            PaperParseTask task = loadTask(taskId);
            if (task == null) {
                return;
            }
            task.setStatus("RUNNING");
            task.setMessage("正在识别图片");
            task.setUpdatedAt(System.currentTimeMillis());
            saveTask(task);

            PaperParseResult result = paperParseService.complete(prepared, (done, total) -> {
                task.setDone(done);
                task.setTotal(total);
                task.setMessage("已处理 " + done + "/" + total + " 张图片");
                task.setUpdatedAt(System.currentTimeMillis());
                saveTask(task);
            });

            task.setResult(result);
            task.setStatus("SUCCESS");
            task.setDone(task.getTotal());
            task.setMessage(result.getQuestions().isEmpty()
                    ? "识别完成，但未拆出题目，请手动录入"
                    : "识别完成，共 " + result.getQuestions().size() + " 道题，请逐题核对");
            task.setUpdatedAt(System.currentTimeMillis());
            saveTask(task);
        } catch (Exception e) {
            log.error("试卷解析任务失败, taskId={}", taskId, e);
            PaperParseTask task = loadTask(taskId);
            if (task != null) {
                task.setStatus("FAILED");
                task.setMessage("解析失败：" + e.getMessage());
                task.setUpdatedAt(System.currentTimeMillis());
                saveTask(task);
            }
        } finally {
            clearRunning(owner);
        }
    }

    private String findRunningTask(String owner) {
        String local = localRunning.get(owner);
        return local != null ? local : redisUtil.get(RUNNING_KEY_PREFIX + owner);
    }

    private boolean isRunning(String status) {
        return "PENDING".equals(status) || "RUNNING".equals(status);
    }

    private void clearRunning(String owner) {
        localRunning.remove(owner);
        redisUtil.delete(RUNNING_KEY_PREFIX + owner);
    }

    private void saveTask(PaperParseTask task) {
        String json = JSONUtil.toJsonStr(task);
        localTasks.put(task.getTaskId(), json);
        redisUtil.set(TASK_KEY_PREFIX + task.getTaskId(), json, TASK_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private PaperParseTask loadTask(String taskId) {
        if (taskId == null) {
            return null;
        }
        String json = localTasks.get(taskId);
        if (json == null) {
            json = redisUtil.get(TASK_KEY_PREFIX + taskId);
            if (json != null) {
                localTasks.put(taskId, json);
            }
        }
        if (json == null) {
            return null;
        }
        try {
            return JSONUtil.toBean(json, PaperParseTask.class);
        } catch (Exception e) {
            log.warn("解析任务反序列化失败, taskId={}, err={}", taskId, e.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
