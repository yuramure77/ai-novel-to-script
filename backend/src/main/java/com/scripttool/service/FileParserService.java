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
     */
    private String parseEpub(MultipartFile file) throws Exception {
        var sb = new StringBuilder();
        try (var zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm")) {
                    String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    String text = stripXml(content);
                    if (!text.isBlank()) {
                        sb.append(text).append("\n\n");
                    }
                }
                zis.closeEntry();
            }
        }
        return sb.toString().trim();
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
