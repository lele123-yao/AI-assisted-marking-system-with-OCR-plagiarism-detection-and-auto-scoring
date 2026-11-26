"""本地 OCR HTTP 服务：RapidOCR(PP-OCRv4) + Flask。

为什么单独起一个 Python 进程：
- Java 后端只依赖一个 HTTP 契约（paper-parse.ocr-url），OCR 引擎可插拔；
- RapidOCR 基于 ONNX Runtime，纯本地推理，不需要联网申请 Key，适合演示环境。

契约（与后端 OcrClient 对齐）：
- POST /ocr，multipart 表单字段 file；
- 可选请求头 X-API-Key（设置 OCR_API_KEY 环境变量后强校验）；
- 返回 {"text": "按行拼接的识别文本"}；
- 并发打满且等待超时返回 429 {"code": "OCR_BUSY"}，让上游快速失败走 MQ 重试，而不是排队拖死。

并发模型（为什么需要"引擎池"而不是简单信号量）：
- RapidOCR/ONNX 会话在多线程并发推理时表现不稳定，甚至崩溃，因此每个并发槽位独占一个引擎实例；
- 用 queue.Queue 做容量有限的池：取不到引擎即说明并发已满，等待 OCR_ACQUIRE_TIMEOUT 秒仍取不到就返回 429；
- 池大小 = OCR_MAX_CONCURRENCY（默认 2）。这是"单进程"上限，多副本/多 worker 时总并发是乘积。

启动方式：
- 开发调试：pip install -r requirements.txt && python server.py
- 生产部署：gunicorn -c gunicorn.conf.py server:app（见同目录 gunicorn.conf.py）
  注意 gunicorn 多 worker 时每个 worker 会各自建一个引擎池，总并发 = workers × OCR_MAX_CONCURRENCY。
"""
import logging
import os
import queue
import threading
import time

import cv2
import numpy as np
from flask import Flask, jsonify, request
from rapidocr_onnxruntime import RapidOCR

logger = logging.getLogger("ocr-server")


def _env_int(name, default):
    try:
        return int(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


def _env_float(name, default):
    try:
        return float(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


class EnginePool:
    """固定容量的 OCR 引擎池。

    - engines：N 个独立的 RapidOCR 实例，每个请求独占一个，避免 ONNX 会话被多线程共享；
    - pool：queue.Queue(maxsize=N)，存放空闲引擎；get 超时即认为并发已满；
    - inflight：当前正在推理的请求数；waiting：当前正在排队等引擎的请求数。

    统计字段用于 /health 暴露，方便排查"到底是并发满了还是引擎挂了"。
    """

    def __init__(self, size, acquire_timeout):
        self.size = max(1, size)
        self.acquire_timeout = acquire_timeout
        self._pool = queue.Queue(maxsize=self.size)
        self._lock = threading.Lock()
        self._inflight = 0
        self._waiting = 0
        self._rejected = 0
        for _ in range(self.size):
            self._pool.put(RapidOCR())

    def acquire(self):
        """取一个引擎；超时返回 None（调用方据此回 429）。"""
        with self._lock:
            self._waiting += 1
        try:
            engine = self._pool.get(timeout=self.acquire_timeout)
        except queue.Empty:
            with self._lock:
                self._rejected += 1
            logger.warning("引擎池已满，等待 %.1fs 仍无空闲引擎，拒绝本次请求", self.acquire_timeout)
            return None
        finally:
            with self._lock:
                self._waiting -= 1
        with self._lock:
            self._inflight += 1
        return engine

    def release(self, engine):
        """归还引擎；无论推理成功或异常都必须调用。"""
        with self._lock:
            self._inflight -= 1
        self._pool.put(engine)

    def stats(self):
        with self._lock:
            return {
                "poolSize": self.size,
                "idle": self._pool.qsize(),
                "inflight": self._inflight,
                "waiting": self._waiting,
                "rejected": self._rejected,
            }


MAX_FILE_MB = _env_int("OCR_MAX_FILE_MB", 20)
API_KEY = os.getenv("OCR_API_KEY", "").strip()

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = MAX_FILE_MB * 1024 * 1024

pool = EnginePool(
    size=_env_int("OCR_MAX_CONCURRENCY", 2),
    acquire_timeout=_env_float("OCR_ACQUIRE_TIMEOUT", 10.0),
)


@app.get("/health")
def health():
    """健康检查同时暴露并发指标，供 Nginx/运维判断服务是否过载。"""
    return jsonify({"status": "up", "concurrency": pool.stats()})


def prepare_image(content):
    """适度放大低分辨率图片，改善手机截图中小字号题干的识别。"""
    array = np.frombuffer(content, dtype=np.uint8)
    image = cv2.imdecode(array, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("无法解码图片")
    height, width = image.shape[:2]
    scale = min(2.5, 1600.0 / width) if width < 1600 else 1.0
    # 避免长截图放大后占用过多内存；原图已经足够清晰时保持原尺寸。
    scale = min(scale, 3600.0 / max(height, width))
    if scale > 1.05:
        image = cv2.resize(
            image, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC
        )
    return image


def restore_reading_order(result):
    """按 OCR 坐标恢复自上而下、同一行从左到右的阅读顺序。"""
    items = []
    for box, text, _ in result or []:
        text = text.strip()
        if not text:
            continue
        points = np.asarray(box, dtype=float)
        left = float(points[:, 0].min())
        right = float(points[:, 0].max())
        top = float(points[:, 1].min())
        bottom = float(points[:, 1].max())
        items.append({
            "text": text,
            "left": left,
            "right": right,
            "top": top,
            "center_y": (top + bottom) / 2,
            "height": max(bottom - top, 1.0),
        })

    rows = []
    for item in sorted(items, key=lambda value: (value["center_y"], value["left"])):
        if rows:
            row = rows[-1]
            row_center = sum(value["center_y"] for value in row) / len(row)
            tolerance = max(8.0, min(item["height"], max(v["height"] for v in row)) * 0.65)
            if abs(item["center_y"] - row_center) <= tolerance:
                row.append(item)
                continue
        rows.append([item])

    lines = []
    for row in rows:
        row.sort(key=lambda value: value["left"])
        line = ""
        previous = None
        typical_height = sum(value["height"] for value in row) / len(row)
        for item in row:
            if previous is not None:
                gap = item["left"] - previous["right"]
                # 近距离碎片直接拼接；较远的栏位保留空格，避免选项粘在一起。
                line += "    " if gap > typical_height * 1.2 else ""
            line += item["text"]
            previous = item
        if line:
            lines.append(line)
    return lines


@app.post("/ocr")
def ocr():
    # 可选鉴权：设置了 OCR_API_KEY 才校验，后端 OcrClient 已支持发送 X-API-Key
    if API_KEY and request.headers.get("X-API-Key", "") != API_KEY:
        return jsonify({"error": "鉴权失败", "code": "UNAUTHORIZED"}), 401

    file = request.files.get("file")
    if file is None:
        return jsonify({"error": "缺少 file 字段"}), 400
    try:
        image = prepare_image(file.read())
    except ValueError as error:
        return jsonify({"error": str(error)}), 400

    engine = pool.acquire()
    if engine is None:
        # 429 让后端 OcrClient 快速失败，转由 mark_task 重试/死信，避免请求长时间挂起
        return jsonify({"error": "OCR 服务繁忙，请稍后重试", "code": "OCR_BUSY"}), 429

    started = time.time()
    try:
        result, _ = engine(image)
        return jsonify({"text": "\n".join(restore_reading_order(result))})
    except Exception as error:  # 引擎异常不应拖垮 worker
        logger.exception("OCR 推理异常: %s", error)
        return jsonify({"error": "OCR 识别失败", "code": "OCR_ERROR"}), 500
    finally:
        pool.release(engine)
        logger.info("OCR 完成: %.2fs", time.time() - started)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    # 开发模式：Flask 自带服务器 + 多线程；并发上限由上面的引擎池兜底控制。
    # 生产请改用 gunicorn（gthread worker），见 gunicorn.conf.py。
    app.run(host="0.0.0.0", port=9100, threaded=True)
