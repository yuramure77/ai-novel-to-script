#!/bin/bash
LOG=/opt/ai-novel-to-script/deploy.log
REPO=/root/ai-novel-to-script

echo "=== $(date) Deploy triggered ===" >> $LOG
cd $REPO
git pull origin main >> $LOG 2>&1
bash build.sh >> $LOG 2>&1
cp backend/target/*.jar /opt/ai-novel-to-script/app.jar
systemctl restart novel-script
echo "=== $(date) Deploy done ===" >> $LOG
