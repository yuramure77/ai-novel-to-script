#!/bin/bash
set -e
echo "=== Build & Deploy ==="

# 1. Build frontend
echo "[1/4] Frontend..."
cd "$(dirname "$0")/frontend"
npm install --silent 2>/dev/null
npm run build

# 2. Copy to backend static
echo "[2/4] Copy to backend..."
rm -rf ../backend/src/main/resources/static/*
cp -r dist/* ../backend/src/main/resources/static/

# 3. Build JAR
echo "[3/4] Maven package..."
cd ../backend
mvn clean package -DskipTests -q

# 4. Deploy to production
if [ -f /opt/ai-novel-to-script/app.jar ]; then
    echo "[4/4] Deploy + restart..."
    cp target/ai-novel-to-script-1.0.0.jar /opt/ai-novel-to-script/app.jar
    systemctl restart novel-script 2>/dev/null || true
    echo "Done!"
else
    echo "[4/4] Skip (no /opt target)"
    echo "JAR: $(pwd)/target/ai-novel-to-script-1.0.0.jar"
fi
