# =====================================================================
# MySQL root 密码重置脚本（重置为 root，与 application.yml 配置一致）
# 适用：忘记 MySQL root 密码、或当前密码与配置不符，导致后端连不上数据库。
# 说明：只改 root 密码，不影响任何数据库数据。
#
# 使用方法（必须以管理员身份运行）：
#   1. 开始菜单搜 "PowerShell"，右键 -> 以管理员身份运行
#   2. 执行：
#        powershell -ExecutionPolicy Bypass -File "d:\ai-marking-system\sql\reset-mysql-password.ps1"
#   3. 看到 "验证连接 OK" 即成功，然后回到对话告诉我"已重置"
# =====================================================================

$ErrorActionPreference = 'Stop'
$mysqlDir = 'D:\java\mysql-8.0.34-winx64'
$mysqld   = Join-Path $mysqlDir 'bin\mysqld.exe'
$mysqlCli = Join-Path $mysqlDir 'bin\mysql.exe'
$initFile = Join-Path $env:TEMP 'mysql-reset-pwd.sql'

if (-not (Test-Path $mysqld)) { Write-Error "找不到 mysqld.exe: $mysqld"; exit 1 }

# 1. 写 init-file：MySQL 启动时会以高权限执行此文件中的语句
"ALTER USER 'root'@'localhost' IDENTIFIED BY 'root';" | Out-File -FilePath $initFile -Encoding ascii -NoNewline

Write-Host '[1/4] 停止 MySQL 服务...'
net stop MySQL 2>$null | Out-Null
Start-Sleep -Seconds 2
# 确保没有残留 mysqld 占用 3306
Get-Process mysqld -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

Write-Host '[2/4] 临时启动 mysqld 执行密码重置...'
$p = Start-Process -FilePath $mysqld -ArgumentList "--init-file=`"$initFile`"", "--console" -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 10

Write-Host '[3/4] 停止临时实例，重新启动 MySQL 服务...'
Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
Get-Process mysqld -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2
net start MySQL | Out-Null
Start-Sleep -Seconds 3

Write-Host '[4/4] 验证新密码 root 能否登录...'
$out = & $mysqlCli -h 127.0.0.1 -uroot -proot -e "SELECT 'OK' AS result;" 2>&1
if ($LASTEXITCODE -eq 0 -and "$out" -match 'OK') {
    Write-Host '========================================' -ForegroundColor Green
    Write-Host ' 成功：root 密码已重置为 root' -ForegroundColor Green
    Write-Host '========================================' -ForegroundColor Green
} else {
    Write-Host '验证失败，请把上方输出贴给我：' -ForegroundColor Red
    Write-Host $out
}
Remove-Item $initFile -ErrorAction SilentlyContinue
