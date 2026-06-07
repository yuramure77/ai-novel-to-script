package com.scripttool.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("分章服务 — 7级策略")
class ChapterSplitServiceTest {

    private final ChapterSplitService service = new ChapterSplitService();

    private String body() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++)
            sb.append("这是一段用于填充章节内容的长文本它包含了丰富的描写和细节。");
        return sb.toString();
    }

    @Test @DisplayName("中文第X章 — 3章识别")
    void chineseChapters() {
        String text = "第一章 春雪\n\n" + body() + "\n\n第二章 青梅竹马\n\n" + body()
            + "\n\n第三章 离别\n\n" + body();
        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertTrue(chapters.size() >= 3, "应识别3章, 实际:" + chapters.size());
    }

    @Test @DisplayName("第X部 — 3部识别")
    void sectionMarkers() {
        String text = "第一部 少年时代\n\n" + body() + "\n\n第二部 青春之门\n\n" + body()
            + "\n\n第三部 命运\n\n" + body();
        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertTrue(chapters.size() >= 3, "应识别3部, 实际:" + chapters.size());
    }

    @Test @DisplayName("fallback无章节长文本分块")
    void fallbackLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 150; i++)
            sb.append("段落").append(i+1).append("内容描写。").append(body()).append("\n\n");
        List<ChapterSplitService.ChapterResult> chapters = service.split(sb.toString());
        assertTrue(chapters.size() >= 3, "长文本应分块, 实际:" + chapters.size());
    }

    @Test @DisplayName("空文本/null安全")
    void nullSafe() {
        assertNotNull(service.split(""));
        assertNotNull(service.split(null));
    }

    @Test @DisplayName("每章标题和内容非空")
    void chaptersHaveContent() {
        String text = "第一章 A\n\n" + body() + "\n\n第二章 B\n\n" + body()
            + "\n\n第三章 C\n\n" + body();
        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertFalse(chapters.isEmpty());
        chapters.forEach(ch -> {
            assertNotNull(ch.title());
            assertFalse(ch.content().isBlank());
            assertTrue(ch.content().length() >= 50);
        });
    }
}
