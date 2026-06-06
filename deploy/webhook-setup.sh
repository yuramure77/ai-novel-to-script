#!/bin/bash
# GitHub Webhook 一键配置
# 在你的服务器上运行此脚本
set -e

echo "=== 配置 GitHub Webhook 自动部署 ==="

# 1. 安装 webhook 工具
if ! command -v webhook &> /dev/null; then
    echo "[1/4] 安装 webhook..."
    yum install -y golang 2>/dev/null || apt install -y golang 2>/dev/null
    go install github.com/adnanh/webhook@latest
    cp ~/go/bin/webhook /usr/local/bin/
fi

# 2. 克隆项目到部署目录
echo "[2/4] 准备部署目录..."
rm -rf /opt/ai-novel-to-script/repo
git clone git@github.com:yuramure77/ai-novel-to-script.git /opt/ai-novel-to-script/repo

# 3. 创建 webhook 配置
echo "[3/4] 创建 Webhook 配置..."
cat > /opt/ai-novel-to-script/hooks.json << 'HOOKS'
[
  {
    "id": "github-deploy",
    "execute-command": "/opt/ai-novel-to-script/deploy/webhook.sh",
    "command-working-directory": "/opt/ai-novel-to-script",
    "response-message": "Deploy triggered",
    "trigger-rule": {
      "match": {
        "type": "payload-hash-sha256",
        "secret": "CHANGE-ME-TO-A-RANDOM-STRING",
        "parameter": {
          "source": "header",
          "name": "X-Hub-Signature-256"
        }
      }
    }
  }
]
HOOKS

# 复制脚本
mkdir -p /opt/ai-novel-to-script/deploy
cp /opt/ai-novel-to-script/repo/deploy/webhook.sh /opt/ai-novel-to-script/deploy/webhook.sh
cp /opt/ai-novel-to-script/repo/deploy/update.sh /opt/ai-novel-to-script/deploy/update.sh
cp /opt/ai-novel-to-script/repo/build.sh /opt/ai-novel-to-script/deploy/build.sh
chmod +x /opt/ai-novel-to-script/deploy/*.sh

# 4. 注册 systemd 自动启动
echo "[4/4] 注册 Webhook 服务..."
cat > /etc/systemd/system/novel-webhook.service << 'UNIT'
[Unit]
Description=GitHub Webhook for Novel Script
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/webhook -hooks /opt/ai-novel-to-script/hooks.json -port 9000 -verbose
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable novel-webhook
systemctl start novel-webhook

WEBHOOK_SECRET=$(openssl rand -hex 16)
echo ""
echo "=== Webhook 配置完成 ==="
echo ""
echo "Webhook Secret: $WEBHOOK_SECRET"
echo "GitHub Webhook URL: http://你的域名:9000/hooks/github-deploy"
echo ""
echo "请在 GitHub 仓库 Settings → Webhooks 中添加："
echo "  Payload URL: http://你的域名:9000/hooks/github-deploy"
echo "  Content type: application/json"
echo "  Secret: $WEBHOOK_SECRET"
echo "  Events: Just the push event"
echo ""
echo "⚠️ 防火墙记得放行 9000 端口"
