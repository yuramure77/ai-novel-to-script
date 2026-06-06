package com.scripttool.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

@RestController
@RequestMapping("/api/deploy")
public class DeployController {

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(@RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event) {
        if (!"push".equals(event) && !"ping".equals(event)) {
            return ResponseEntity.ok(Map.of("status", "ignored", "event", event));
        }

        // Execute deploy in background thread so response doesn't get killed by restart
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "/bin/bash", "/opt/ai-novel-to-script/deploy/webhook.sh"
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[deploy] " + line);
                }
                p.waitFor();
                System.out.println("[deploy] Done, exit code: " + p.exitValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "deploy-thread").start();

        return ResponseEntity.ok(Map.of("status", "deploying"));
    }
}
