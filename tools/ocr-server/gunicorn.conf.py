"""gunicorn 生产配置：把 Flask 开发服务器换成多进程 + 多线程的 WSGI 容器。

启动：
    gunicorn -c gunicorn.conf.py server:app

为什么用 gthread 而不是 sync/gevent：
- sync worker 一个进程同一时刻只处理一个请求，OCR 推理是 CPU 密集 + 等待，吞吐太低；
- gevent 需要 monkey patch，且 ONNX Runtime 的 C 扩展在 greenlet 下不一定安全；
- gthread = 多线程 worker，配合 server.py 里的 EnginePool（每个并发槽独占一个引擎），
  既能并行处理请求，又不会让多个线程共享同一个 ONNX 会话。

并发换算：
    单进程并发上限 = OCR_MAX_CONCURRENCY（server.py 引擎池大小）
    服务总并发上限 = workers × OCR_MAX_CONCURRENCY
    gunicorn 线程数（threads）建议 >= OCR_MAX_CONCURRENCY，否则多余的线程会在池里排队。

所有参数都可用环境变量覆盖，方便容器化部署时按机器核数调整。
"""
import os


def _env_int(name, default):
    try:
        return int(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


# 监听地址：默认所有网卡 9100，便于 Nginx 反向代理到本机/其他副本
bind = os.getenv("OCR_BIND", "0.0.0.0:9100")

# 进程数：默认 2；CPU 核数足够时可调大，但每个 worker 都会加载一份 ONNX 模型，注意内存
workers = _env_int("OCR_WORKERS", 2)

# 每个 worker 的线程数：与引擎池并发上限配合，默认 4
threads = _env_int("OCR_THREADS", 4)

# gthread：多线程 worker，适配 OCR 这种"CPU 密集 + 等待"的混合负载
worker_class = "gthread"

# 单请求超时（秒）：后端 OcrClient 读取超时 30s，这里放宽到 60s 留出排队余量
timeout = _env_int("OCR_TIMEOUT", 60)

# 优雅重启/退出时等待存量请求处理完成的最长时间
graceful_timeout = 30

# 长连接复用：减少频繁建连开销，keepalive 略大于 Nginx 的 upstream keepalive 配置
keepalive = 5

# 访问日志打到 stdout，由容器/系统日志统一收集
accesslog = "-"
errorlog = "-"
loglevel = os.getenv("OCR_LOG_LEVEL", "info")

# 进程名带前缀，便于 ps/top 里一眼区分 OCR 进程
proc_name = "ocr-server"
