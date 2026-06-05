#!/bin/bash
set -e
echo "=== 构建 AI 小说转剧本工具 ==="

# 1. Build frontend
echo "[1/3] 构建前端..."
cd "$(dirname "$0")/frontend"
npm install --silent
npm run build

# 2. Copy frontend dist to backend static
echo "[2/3] 复制前端资源到后端..."
rm -rf ../backend/src/main/resources/static/*
cp -r dist/* ../backend/src/main/resources/static/

# 3. Build backend JAR
echo "[3/3] 打包后端..."
cd ../backend
mvn package -DskipTests -q

echo ""
echo "=== 构建完成 ==="
echo "JAR 包: backend/target/ai-novel-to-script-1.0.0.jar"
echo ""
echo "启动命令:"
echo "  java -jar backend/target/ai-novel-to-script-1.0.0.jar"
echo ""
echo "或使用 Docker:"
echo "  docker-compose up -d"
