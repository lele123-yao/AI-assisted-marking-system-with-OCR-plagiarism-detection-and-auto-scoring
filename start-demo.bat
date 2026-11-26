@echo off
chcp 65001 >nul
setlocal
rem ============================================================
rem 一键启动面试演示环境：OCR 识别服务 + Spring Boot 后端 + Vite 前端
rem 用法：双击本文件，等约 40 秒；弹出的三个窗口都不要关闭。
rem 手机/微信访问：电脑与手机连同一网络，浏览器打开 http://电脑IP:5173/m/login
rem ============================================================

echo [1/3] 启动 OCR 服务（端口 9100）...
start "ai-marking-ocr" cmd /k "cd /d %~dp0tools\ocr-server && python server.py"

echo [2/3] 启动后端（端口 8080）...
start "ai-marking-backend" cmd /k "cd /d %~dp0 && set PAPER_OCR_URL=http://localhost:9100/ocr && mvnw.cmd spring-boot:run"

echo [3/3] 启动前端（端口 5173，已开放局域网访问）...
start "ai-marking-frontend" cmd /k "cd /d %~dp0frontend && npm run dev -- --host 0.0.0.0"

echo.
echo 电脑访问: http://localhost:5173
echo 手机访问: 运行 ipconfig 查看 WLAN 的 IPv4 地址，拼成 http://IP:5173/m/login
echo 账号: admin/123456（教师）  demo_student/123456（学生）
pause
