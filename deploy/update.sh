#!/bin/bash
# 零停机热更新 — 拉取代码 → 重新构建 → 平滑重启
set -e

echo "=== 热更新开始 $(date) ==="

cd "$(dirname "$0")/.."

# 1. Pull latest
echo "[1/4] 拉取最新代码..."
git pull origin main

# 2. Build
echo "[2/4] 重新构建..."
bash build.sh

# 3. Copy new JAR
echo "[3/4] 替换 JAR..."
sudo cp backend/target/ai-novel-to-script-1.0.0.jar /opt/ai-novel-to-script/app.jar

# 4. Zero-downtime reload (nginx buffers during restart)
echo "[4/4] 平滑重启..."
sudo systemctl reload-or-restart novel-script

# Wait for health check
sleep 3
if curl -sf http://localhost:8080/api/projects > /dev/null 2>&1; then
    echo "✅ 更新成功，服务正常"
else
    echo "❌ 服务启动失败，查看日志: sudo journalctl -u novel-script -n 50"
    exit 1
fi

echo "=== 更新完成 $(date) ==="
