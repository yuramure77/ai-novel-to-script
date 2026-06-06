package com.scripttool.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileParserService {

    public String parse(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) throw new RuntimeException("文件名为空");

        String lower = filename.toLowerCase();
        if (lower.endsWith(".txt")) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } else if (lower.endsWith(".pdf")) {
            throw new RuntimeException("PDF 解析暂不支持，请先转换为 TXT 文件后上传");
        } else if (lower.endsWith(".epub")) {
            return parseEpub(file);
        } else if (lower.endsWith(".docx")) {
            return parseDocx(file);
        } else {
            throw new RuntimeException("不支持的文件格式，支持：txt, epub, docx");
        }
    }

    /**
     * DOCX: a ZIP containing word/document.xml
     */
    private String parseDocx(MultipartFile file) throws Exception {
        try (var zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("word/document.xml")) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return stripXml(xml);
                }
                zis.closeEntry();
            }
        }
        throw new RuntimeException("无效的 DOCX 文件：未找到 document.xml");
    }

    /**
     * EPUB: a ZIP containing XHTML/HTML files
     * Handles various encodings and nested directory structures
     */
    private String parseEpub(MultipartFile file) throws Exception {
        var sb = new StringBuilder();
        try (var zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                // Skip non-content files
                if (entry.isDirectory()) { zis.closeEntry(); continue; }
                if (!name.endsWith(".html") && !name.endsWith(".xhtml") && !name.endsWith(".htm")) {
                    zis.closeEntry(); continue;
                }
                // Skip nav/toc/index files
                String baseName = name.substring(name.lastIndexOf('/') + 1);
                if (baseName.startsWith("nav") || baseName.startsWith("toc") || baseName.startsWith("index")) {
                    zis.closeEntry(); continue;
                }
                try {
                    byte[] bytes = zis.readAllBytes();
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    // Handle UTF-8 BOM
                    if (content.startsWith("﻿")) content = content.substring(1);
                    String text = stripXml(content);
                    if (!text.isBlank()) {
                        sb.append(text).append("\n\n");
                    }
                } catch (Exception e) {
                    System.err.println("[epub] Failed to parse entry: " + name + " — " + e.getMessage());
                }
                zis.closeEntry();
            }
        }
        String result = sb.toString().trim();
        if (result.isEmpty()) {
            throw new RuntimeException("EPUB 文件中未找到文本内容，请确认文件格式正确");
        }
        return result;
    }

    /**
     * Strip XML/HTML tags, decode entities, normalize whitespace
     */
    private String stripXml(String xml) {
        return xml.replaceAll("<[^>]+>", " ")
                  .replaceAll("&nbsp;", " ")
                  .replaceAll("&amp;", "&")
                  .replaceAll("&lt;", "<")
                  .replaceAll("&gt;", ">")
                  .replaceAll("&quot;", "\"")
                  .replaceAll("&apos;", "'")
                  .replaceAll("&#\\d+;", " ")
                  .replaceAll("\\s+", " ")
                  .trim();
    }
}
