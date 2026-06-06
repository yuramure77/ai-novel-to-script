package com.scripttool.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class FileParserServiceTest {

    private final FileParserService service = new FileParserService();

    @Test
    void shouldParseTxtFile() throws Exception {
        String content = "第一章 序章\n这是测试内容。";
        var file = new MockMultipartFile("test.txt", "test.txt",
                "text/plain", content.getBytes(StandardCharsets.UTF_8));

        String result = service.parse(file);
        assertEquals(content, result);
    }

    @Test
    void shouldRejectPdfFile() {
        var file = new MockMultipartFile("test.pdf", "test.pdf",
                "application/pdf", new byte[0]);

        assertThrows(RuntimeException.class, () -> service.parse(file));
    }

    @Test
    void shouldRejectUnsupportedFormat() {
        var file = new MockMultipartFile("test.exe", "test.exe",
                "application/octet-stream", new byte[0]);

        assertThrows(RuntimeException.class, () -> service.parse(file));
    }

    @Test
    void shouldRejectNullFilename() {
        var file = new MockMultipartFile("file", null,
                "text/plain", "content".getBytes(StandardCharsets.UTF_8));

        assertThrows(RuntimeException.class, () -> service.parse(file));
    }

    @Test
    void shouldHandleEmptyTxt() throws Exception {
        var file = new MockMultipartFile("test.txt", "test.txt",
                "text/plain", "".getBytes(StandardCharsets.UTF_8));

        String result = service.parse(file);
        assertEquals("", result);
    }

    @Test
    void shouldParseDocxFile() throws Exception {
        // Minimal DOCX: ZIP with word/document.xml
        String docXml = """
                <?xml version="1.0"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>测试内容第一段</w:t></w:r></w:p>
                    <w:p><w:r><w:t>第二段内容</w:t></w:r></w:p>
                  </w:body>
                </w:document>""";

        var baos = new java.io.ByteArrayOutputStream();
        try (var zos = new java.util.zip.ZipOutputStream(baos)) {
            var entry = new java.util.zip.ZipEntry("word/document.xml");
            zos.putNextEntry(entry);
            zos.write(docXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        var file = new MockMultipartFile("test.docx", "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                baos.toByteArray());

        String result = service.parse(file);
        assertTrue(result.contains("测试内容第一段"));
        assertTrue(result.contains("第二段内容"));
    }
}
