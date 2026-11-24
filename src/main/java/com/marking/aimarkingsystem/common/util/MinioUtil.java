package com.marking.aimarkingsystem.common.util;

import cn.hutool.core.util.IdUtil;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.ResultCode;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储工具类：上传、下载、删除、获取访问地址。
 *
 * 防盗链设计说明（本类的关键点）：
 * 1. 桶保持私有（private），不开放匿名读，外部拿不到固定直链；
 * 2. 前端通过 getPresignedUrl 获取"带签名 + 有效期"的临时地址，
 *    过期后链接自动失效，即使被复制到别的站点也无法长期盗用；
 * 3. 签名地址里包含对象键，配合私有桶，无法绕过签名直接枚举资源。
 * 若后续接入 CDN，可再叠加 Referer 白名单，但核心仍是"私有桶 + 短时效签名"。
 *
 * 说明：
 * - 本类只做存储层封装，不涉及答卷业务；
 * - 数据库只存 objectKey（如 answer/2026/11/12/xxx.jpg），不存签名 URL（会过期）。
 */
@Slf4j
@Component
public class MinioUtil {

    /** 未指定目录时的默认前缀 */
    private static final String DEFAULT_DIRECTORY = "answer";

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 预签名 URL 最大有效期：MinIO 限制为 7 天 */
    private static final int MAX_EXPIRY_SECONDS = 7 * 24 * 60 * 60;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.presigned-expiry:3600}")
    private int presignedExpiry;

    /** 本地回退存储目录（MinIO 不可用时落盘到这里） */
    @Value("${storage.local-dir:./data/uploads}")
    private String localDir;

    /** 本地文件的访问前缀，默认走 /api/files，前端无需额外配置 */
    @Value("${storage.local-url-prefix:/api/files}")
    private String localUrlPrefix;

    /** MinIO 不可用时是否自动回退到本地磁盘（面试演示环境建议开启） */
    @Value("${storage.fallback-to-local:true}")
    private boolean fallbackToLocal;

    private MinioClient client;

    /** 桶是否已确认存在，避免每次上传都调一次 bucketExists */
    private volatile boolean bucketReady = false;

    /** 已回退到本地磁盘存储（本进程内一旦判定 MinIO 不可用就不再重试） */
    private volatile boolean localMode = false;

    @PostConstruct
    public void init() {
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        // 刻意不在启动时连接 MinIO：本地开发未启动容器时应用仍可正常启动，
        // 真正的连通性检查推迟到首次上传（ensureBucket 中完成）
        log.info("MinIO 客户端已初始化，endpoint={}, bucket={}", endpoint, bucket);
    }

    /**
     * 上传文件。
     *
     * @param file      上传文件
     * @param directory 对象目录前缀，如 answer / paper，可为空（默认 answer）
     * @return 对象键 objectKey，需写入数据库，后续凭它换签名访问地址
     */
    public String upload(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        ensureBucket();

        String objectName = buildObjectName(directory, file.getOriginalFilename());
        if (localMode) {
            saveLocal(objectName, file);
            return objectName;
        }
        try (InputStream in = file.getInputStream()) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    // partSize 传 -1 表示由 SDK 自动计算分片大小，适合图片等中小文件
                    .stream(in, file.getSize(), -1)
                    .contentType(resolveContentType(file.getContentType()))
                    .build());
            return objectName;
        } catch (Exception e) {
            if (fallbackToLocal) {
                switchToLocal("上传", e);
                saveLocal(objectName, file);
                return objectName;
            }
            log.error("MinIO 上传失败, object={}", objectName, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 上传内存中的字节内容（模块8：POI 生成的 PPT 直接以 byte[] 上传，没有本地文件）。
     *
     * 与 {@link #upload(MultipartFile, String)} 的区别：入口不是上传文件，而是程序生成的内容，
     * 因此需要显式传入文件名（用于推断扩展名）与 contentType。
     *
     * @param content          文件内容，不能为空
     * @param directory        对象目录前缀，如 learning-report
     * @param originalFilename 原始文件名，用于推断扩展名，如 report.pptx
     * @param contentType      MIME 类型，如 PPT 的 application/vnd.openxmlformats-...
     * @return 对象键 objectKey
     */
    public String uploadBytes(byte[] content, String directory, String originalFilename, String contentType) {
        if (content == null || content.length == 0) {
            throw new BusinessException("上传内容不能为空");
        }
        ensureBucket();

        String objectName = buildObjectName(directory, originalFilename);
        if (localMode) {
            saveLocal(objectName, content);
            return objectName;
        }
        try (InputStream in = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    // 已知字节长度，partSize 传 -1 由 SDK 自动决定分片
                    .stream(in, content.length, -1)
                    .contentType(resolveContentType(contentType))
                    .build());
            return objectName;
        } catch (Exception e) {
            if (fallbackToLocal) {
                switchToLocal("字节上传", e);
                saveLocal(objectName, content);
                return objectName;
            }
            log.error("MinIO 字节上传失败, object={}, size={}B", objectName, content.length, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 下载文件。返回的流由调用方负责关闭（建议 try-with-resources）。
     */
    public InputStream download(String objectName) {
        requireObjectName(objectName);
        Path local = localPath(objectName);
        if (Files.exists(local)) {
            try {
                return Files.newInputStream(local);
            } catch (IOException e) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件读取失败：" + e.getMessage());
            }
        }
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 下载失败, object={}", objectName, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件下载失败：" + e.getMessage());
        }
    }

    /**
     * 删除文件。删除不存在的对象不会报错，符合幂等语义。
     */
    public void delete(String objectName) {
        requireObjectName(objectName);
        Path local = localPath(objectName);
        if (Files.exists(local)) {
            try {
                Files.delete(local);
            } catch (IOException e) {
                log.warn("本地文件删除失败, path={}, err={}", local, e.getMessage());
            }
        }
        if (localMode) {
            return;
        }
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 删除失败, object={}", objectName, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件删除失败：" + e.getMessage());
        }
    }

    /**
     * 获取带签名的临时访问地址（默认有效期取配置项 minio.presigned-expiry）。
     */
    public String getPresignedUrl(String objectName) {
        return getPresignedUrl(objectName, presignedExpiry);
    }

    /**
     * 获取带签名的临时访问地址。
     *
     * @param expireSeconds 有效期（秒），范围 1 ~ 7 天
     */
    public String getPresignedUrl(String objectName, int expireSeconds) {
        requireObjectName(objectName);
        if (Files.exists(localPath(objectName)) || localMode) {
            String prefix = localUrlPrefix.endsWith("/")
                    ? localUrlPrefix.substring(0, localUrlPrefix.length() - 1)
                    : localUrlPrefix;
            return prefix + "/" + objectName;
        }
        if (expireSeconds <= 0 || expireSeconds > MAX_EXPIRY_SECONDS) {
            throw new BusinessException("签名有效期必须在 1 秒到 7 天之间");
        }
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectName)
                    .expiry(expireSeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 获取签名地址失败, object={}", objectName, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取文件访问地址失败：" + e.getMessage());
        }
    }

    /** 对象是否存在（用于下载前判断，可选调用） */
    public boolean exists(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return false;
        }
        if (Files.exists(localPath(objectName))) {
            return true;
        }
        if (localMode) {
            return false;
        }
        try {
            client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取当前配置的桶名，供业务层拼接展示信息时使用 */
    public String getBucket() {
        return bucket;
    }

    // ==================== 内部方法 ====================

    /** 确保桶存在；只在首次上传时执行一次 */
    private synchronized void ensureBucket() {
        if (bucketReady || localMode) {
            return;
        }
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.warn("MinIO 桶 {} 不存在，已自动创建（默认私有权限）", bucket);
            }
            bucketReady = true;
        } catch (Exception e) {
            if (fallbackToLocal) {
                switchToLocal("建桶检查", e);
                return;
            }
            log.error("MinIO 连接或建桶失败, endpoint={}, bucket={}", endpoint, bucket, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "MinIO 存储不可用：" + e.getMessage());
        }
    }

    /** 切换到本地磁盘存储（进程内只提示一次） */
    private void switchToLocal(String scene, Exception cause) {
        localMode = true;
        log.warn("MinIO 不可用（{}），已回退到本地文件存储: dir={}, reason={}",
                scene, localDir, cause.getMessage());
    }

    /** 本地文件绝对路径，同时拦截 ../ 之类的越界访问 */
    private Path localPath(String objectName) {
        Path base = Paths.get(localDir).toAbsolutePath().normalize();
        Path target = base.resolve(objectName).normalize();
        if (!target.startsWith(base)) {
            throw new BusinessException("非法的文件路径");
        }
        return target;
    }

    private void saveLocal(String objectName, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            saveLocal(objectName, in);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "本地文件保存失败：" + e.getMessage());
        }
    }

    private void saveLocal(String objectName, byte[] content) {
        try {
            saveLocal(objectName, new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "本地文件保存失败：" + e.getMessage());
        }
    }

    private void saveLocal(String objectName, InputStream in) throws IOException {
        Path target = localPath(objectName);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** 生成对象键：目录/年/月/日/UUID.扩展名，避免同名覆盖并便于按日期归档 */
    private String buildObjectName(String directory, String originalFilename) {
        String dir = StringUtils.hasText(directory)
                ? directory.trim().replaceAll("^/+|/+$", "")
                : DEFAULT_DIRECTORY;
        String extension = resolveExtension(originalFilename);
        String fileName = IdUtil.fastSimpleUUID()
                + (extension == null ? "" : "." + extension);
        return dir + "/" + LocalDate.now().format(DATE_PATH) + "/" + fileName;
    }

    private String resolveExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private String resolveContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }

    private void requireObjectName(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            throw new BusinessException("文件对象键不能为空");
        }
    }
}
