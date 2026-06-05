#!/bin/bash
# 一键部署脚本 — 在服务器上运行
# Usage: ./deploy.sh your-domain.com
set -e

DOMAIN=${1:-localhost}
APP_DIR=/opt/ai-novel-to-script
JAR_FILE=backend/target/ai-novel-to-script-1.0.0.jar

echo "=== AI 小说转剧本 — 生产部署 ==="
echo "域名: $DOMAIN"

# 1. Build
echo "[1/6] 构建项目..."
cd "$(dirname "$0")/.."
bash build.sh

# 2. Create app directory
echo "[2/6] 创建应用目录..."
sudo mkdir -p $APP_DIR/data
sudo cp $JAR_FILE $APP_DIR/app.jar

# 3. Configure Nginx
echo "[3/6] 配置 Nginx..."
sudo cp deploy/nginx.conf /etc/nginx/sites-available/novel-script
sudo sed -i "s/your-domain.com/$DOMAIN/g" /etc/nginx/sites-available/novel-script
sudo ln -sf /etc/nginx/sites-available/novel-script /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# 4. HTTPS (optional, requires DNS to be pointing)
if [ "$DOMAIN" != "localhost" ]; then
    echo "[4/6] 配置 HTTPS..."
    sudo certbot --nginx -d $DOMAIN --non-interactive --agree-tos -m admin@$DOMAIN || echo "HTTPS skipped (run certbot manually if DNS not ready)"
else
    echo "[4/6] HTTPS skipped (no domain)"
fi

# 5. Systemd service
echo "[5/6] 注册系统服务..."
sudo cp deploy/app.service /etc/systemd/system/novel-script.service
sudo systemctl daemon-reload
sudo systemctl enable novel-script
sudo systemctl restart novel-script

# 6. Done
echo "[6/6] 部署完成！"
echo ""
echo "访问地址: https://$DOMAIN"
echo ""
echo "管理命令:"
echo "  查看状态: sudo systemctl status novel-script"
echo "  查看日志: sudo journalctl -u novel-script -f"
echo "  重启服务: sudo systemctl restart novel-script"
echo "  停止服务: sudo systemctl stop novel-script"
