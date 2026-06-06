package com.scripttool.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    // ── DOCX export (ZIP + XML, zero dependencies) ──

    public byte[] yamlToDocx(String yaml) {
        String title = coalesce(extract(yaml, "title:"), "未命名剧本");
        String author = coalesce(extract(yaml, "author:"), "未知");

        StringBuilder paragraphs = new StringBuilder();
        Pattern sceneP = Pattern.compile("- id: (SCENE_\\d+)\\n(.*?)(?=\\n  - id: SCENE_|\\Z)", Pattern.DOTALL);
        Matcher sm = sceneP.matcher(yaml);

        paragraphs.append(wp("标题: " + title, true, 28));
        paragraphs.append(wp("作者: " + author, false, 20));
        paragraphs.append(wp("", false, 12));

        while (sm.find()) {
            String sceneBody = sm.group(2);
            String loc = coalesce(extract(sceneBody, "location:"), "—");
            String time = coalesce(extract(sceneBody, "time:"), "");
            String desc = coalesce(extract(sceneBody, "description:"), "");
            paragraphs.append(wp("📍 " + loc + " · " + time, true, 18));
            if (!desc.isEmpty()) paragraphs.append(wp(desc, false, 14));

            Pattern beatP = Pattern.compile("- type:\\s*(\\w+)\\n(.*?)(?=\\n      - type:|\\n  - id:|\\Z)", Pattern.DOTALL);
            Matcher bm = beatP.matcher(sceneBody);
            while (bm.find()) {
                String bType = bm.group(1);
                String bBody = bm.group(2);
                String chr = coalesce(extract(bBody, "character:"), "");
                String line = coalesce(extract(bBody, "line:"), "");
                String dir = coalesce(extract(bBody, "direction:"), "");
                String emo = coalesce(extract(bBody, "emotion:"), "");

                switch (bType) {
                    case "action" -> { if (!dir.isEmpty()) paragraphs.append(wp("【" + dir + "】", false, 13)); }
                    case "dialogue" -> paragraphs.append(wp(chr + emo() + ": " + line, false, 14));
                    case "monologue" -> paragraphs.append(wp(chr + "（独白）: " + line, false, 14));
                    case "narration" -> paragraphs.append(wp("旁白: " + line, false, 14));
                    case "transition" -> { if (!dir.isEmpty()) paragraphs.append(wp("—— " + dir + " ——", false, 13)); }
                }
            }
        }

        try {
            var baos = new ByteArrayOutputStream();
            try (var zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zos.write(CT_XML.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("_rels/.rels"));
                zos.write(RELS_XML.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("word/document.xml"));
                zos.write(DOC_XML.formatted(xmlEscape(title), paragraphs.toString()).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("DOCX生成失败", e);
        }
    }

    private String wp(String text, boolean bold, int size) {
        if (text == null || text.isBlank()) return "";
        String bTag = bold ? "<w:b/><w:bCs/>" : "";
        return "<w:p><w:pPr><w:rPr><w:rFonts w:ascii=\"SimSun\" w:hAnsi=\"SimSun\"/><w:sz w:val=\"" + (size * 2) + "\"/>" + bTag + "</w:rPr></w:pPr><w:r><w:rPr><w:rFonts w:ascii=\"SimSun\" w:hAnsi=\"SimSun\"/><w:sz w:val=\"" + (size * 2) + "\"/></w:rPr><w:t xml:space=\"preserve\">" + xmlEscape(text) + "</w:t></w:r></w:p>";
    }

    private String emo() { return ""; }

    private String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String CT_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>""";

    private static final String RELS_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>""";

    private static final String DOC_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>%s</w:body>
            </w:document>""";

    // ── HTML export (print-friendly, for PDF via browser) ──

    public String yamlToHtml(String yaml) {
        String markdown = yamlToMarkdown(yaml);
        return """
            <!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>剧本导出</title>
            <style>
              body { font-family: 'Noto Serif SC', Georgia, serif; max-width: 800px; margin: 40px auto; padding: 20px; color: #333; line-height: 1.8; }
              h1 { text-align: center; font-size: 24px; margin-bottom: 4px; }
              h2 { font-size: 18px; margin-top: 32px; border-bottom: 1px solid #ddd; padding-bottom: 4px; }
              h3 { font-size: 15px; margin-top: 24px; color: #555; }
              table { width: 100%%; border-collapse: collapse; margin: 16px 0; }
              th, td { border: 1px solid #ddd; padding: 6px 10px; text-align: left; font-size: 13px; }
              th { background: #f5f5f5; }
              blockquote { border-left: 3px solid #ddd; margin: 8px 0; padding: 4px 12px; color: #666; font-style: italic; }
              strong { color: #222; }
              @media print { body { margin: 0; padding: 0; } }
            </style></head><body>
            %s
            </body></html>""".formatted(markdown
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\n\n", "</p><p>").replace("\n", "<br>"));
    }
}
