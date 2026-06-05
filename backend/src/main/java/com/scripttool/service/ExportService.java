package com.scripttool.service;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExportService {

    public String yamlToMarkdown(String yaml) {
        var sb = new StringBuilder();
        String title = extract(yaml, "title:");
        String author = extract(yaml, "author:");
        String basedOn = extract(yaml, "based_on:");

        sb.append("# ").append(coalesce(title, "剧本")).append("\n\n");
        sb.append("**原著**: ").append(coalesce(basedOn, "—")).append("  \n");
        sb.append("**改编**: ").append(coalesce(author, "—")).append("  \n\n");
        sb.append("---\n\n");

        // Characters section
        sb.append("## 角色表\n\n");
        sb.append("| 角色 | 类型 | 描述 | 特征 |\n");
        sb.append("|------|------|------|------|\n");
        Pattern charP = Pattern.compile("- name:\\s*(.+?)\\n(.*?)(?=\\n  - name:|\\nscenes:|\\Z)", Pattern.DOTALL);
        Matcher cm = charP.matcher(yaml);
        while (cm.find()) {
            String name = cm.group(1).replace("\"", "").trim();
            String block = cm.group(2);
            String role = extract(block, "role:");
            String desc = extract(block, "description:");
            String traits = extractTraits(block);
            sb.append("| ").append(name).append(" | ").append(roleLabel(role))
              .append(" | ").append(coalesce(desc, "—"))
              .append(" | ").append(coalesce(traits, "—")).append(" |\n");
        }
        sb.append("\n---\n\n");

        // Scenes
        sb.append("## 场景\n\n");
        Pattern sceneP = Pattern.compile("- id: (SCENE_\\d+)\\n(.*?)(?=\\n  - id: SCENE_|\\Z)", Pattern.DOTALL);
        Matcher sm = sceneP.matcher(yaml);
        while (sm.find()) {
            String sceneBody = sm.group(2);
            String sceneNum = extract(sceneBody, "scene_number:");
            String location = extract(sceneBody, "location:");
            String time = extract(sceneBody, "time:");
            String desc = extract(sceneBody, "description:");
            String type = extract(sceneBody, "type:");

            sb.append("### 场景 ").append(coalesce(sceneNum, "?")).append(": ")
              .append(coalesce(location, "未知地点")).append("\n\n");
            sb.append("*").append(coalesce(type, "—")).append(" · ").append(coalesce(time, "—"));
            if (desc != null) sb.append(" · *").append(desc).append("*");
            sb.append("\n\n");

            // Beats
            Pattern beatP = Pattern.compile("- type:\\s*(\\w+)\\n(.*?)(?=\\n      - type:|\\n  - id:|\\Z)", Pattern.DOTALL);
            Matcher bm = beatP.matcher(sceneBody);
            while (bm.find()) {
                String bType = bm.group(1);
                String bBody = bm.group(2);
                String character = extract(bBody, "character:");
                String line = extract(bBody, "line:");
                String direction = extract(bBody, "direction:");
                String emotion = extract(bBody, "emotion:");

                switch (bType) {
                    case "action":
                        if (direction != null) sb.append("> *").append(direction).append("*\n\n");
                        break;
                    case "dialogue":
                        sb.append("**").append(coalesce(character, "?")).append("**");
                        if (emotion != null) sb.append("（").append(emotion).append("）");
                        sb.append(": ");
                        if (line != null) sb.append(line);
                        if (direction != null) sb.append(" *(").append(direction).append(")*");
                        sb.append("\n\n");
                        break;
                    case "monologue":
                        sb.append("**").append(coalesce(character, "?")).append("（独白）**: ");
                        if (line != null) sb.append(line);
                        sb.append("\n\n");
                        break;
                    case "narration":
                        sb.append("> 旁白: ").append(coalesce(line, "—")).append("\n\n");
                        break;
                    case "transition":
                        if (direction != null) sb.append("> *转场: ").append(direction).append("*\n\n");
                        break;
                }
            }
            sb.append("---\n\n");
        }

        return sb.toString();
    }

    public String yamlToFountain(String yaml) {
        var sb = new StringBuilder();
        String title = extract(yaml, "title:");
        String author = extract(yaml, "author:");
        sb.append("Title: ").append(coalesce(title, "未命名")).append("\n");
        sb.append("Author: ").append(coalesce(author, "未知")).append("\n\n");
        sb.append("===\n\n");

        Pattern sceneP = Pattern.compile("- id: (SCENE_\\d+)\\n(.*?)(?=\\n  - id: SCENE_|\\Z)", Pattern.DOTALL);
        Matcher sm = sceneP.matcher(yaml);
        while (sm.find()) {
            String sceneBody = sm.group(2);
            String location = extract(sceneBody, "location:");
            String time = extract(sceneBody, "time:");

            sb.append(".").append(coalesce(location, "—")).append(" - ").append(coalesce(time, "—")).append("\n\n");

            Pattern beatP = Pattern.compile("- type:\\s*(\\w+)\\n(.*?)(?=\\n      - type:|\\n  - id:|\\Z)", Pattern.DOTALL);
            Matcher bm = beatP.matcher(sceneBody);
            while (bm.find()) {
                String bType = bm.group(1);
                String bBody = bm.group(2);
                String character = extract(bBody, "character:");
                String line = extract(bBody, "line:");
                String direction = extract(bBody, "direction:");
                String emotion = extract(bBody, "emotion:");

                switch (bType) {
                    case "action":
                        if (direction != null) sb.append(direction).append("\n\n");
                        break;
                    case "dialogue":
                        sb.append(coalesce(character, "?"));
                        if (emotion != null) sb.append(" (").append(emotion).append(")");
                        sb.append("\n").append(coalesce(line, "")).append("\n\n");
                        break;
                    case "monologue":
                        sb.append(coalesce(character, "?")).append(" (V.O.)\n").append(coalesce(line, "")).append("\n\n");
                        break;
                    case "transition":
                        if (direction != null) sb.append("> ").append(direction).append(" <\n\n");
                        break;
                }
            }
        }
        return sb.toString();
    }

    private String extract(String text, String key) {
        Pattern p = Pattern.compile(key + "\\s*\"?([^\"\\n]+)\"?", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String v = m.group(1).trim();
            return v.equals("null") ? null : v;
        }
        return null;
    }

    private String extractTraits(String block) {
        Pattern p = Pattern.compile("- ([\"']?)([^\"',\\n]+)\\1", Pattern.DOTALL);
        Matcher m = p.matcher(block);
        if (!m.find()) return null;
        var sb = new StringBuilder();
        sb.append(m.group(2));
        while (m.find()) {
            sb.append(", ").append(m.group(2));
        }
        return sb.toString();
    }

    private String coalesce(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private String roleLabel(String r) {
        return switch (r) {
            case "protagonist" -> "主角";
            case "antagonist" -> "反派";
            case "supporting" -> "配角";
            case "minor" -> "次要";
            default -> r != null ? r : "未知";
        };
    }
}
