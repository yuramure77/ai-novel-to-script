#!/bin/bash
# GitHub Webhook 自动部署脚本
# 监听 push 事件，自动拉取代码并重新部署

LOG_FILE=/opt/ai-novel-to-script/webhook.log
PROJECT_DIR=/opt/ai-novel-to-script/repo

echo "$(date) Webhook triggered" >> $LOG_FILE

cd $PROJECT_DIR
git pull origin main >> $LOG_FILE 2>&1

bash deploy/update.sh >> $LOG_FILE 2>&1

echo "$(date) Deploy complete" >> $LOG_FILE
