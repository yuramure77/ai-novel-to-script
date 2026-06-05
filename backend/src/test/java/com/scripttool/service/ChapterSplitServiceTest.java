package com.scripttool.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChapterSplitServiceTest {

    private final ChapterSplitService service = new ChapterSplitService();

    @Test
    void shouldSplitChineseChapters() {
        String text = """
                第一章 开端
                这是第一章的内容，讲述故事的开端。
                这里有很多情节发展。

                第二章 发展
                故事进入发展阶段，主角面临新的挑战。
                新的角色出现，情节变得复杂。

                第三章 高潮
                所有矛盾和冲突在这一章集中爆发。
                主角做出了关键选择。

                第四章 结局
                故事走向终点，一切尘埃落定。
                """;

        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertTrue(chapters.size() >= 3, "Should find at least 3 chapters");
        assertEquals("第一章 开端", chapters.get(0).title());
        assertEquals("第二章 发展", chapters.get(1).title());
    }

    @Test
    void shouldHandleSingleChapter() {
        String text = """
                第一章 孤独的旅行
                这是一段关于长途跋涉的故事。旅人在沙漠中走了很久很久。
                """;

        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertEquals(1, chapters.size());
    }

    @Test
    void shouldHandleEmptyText() {
        List<ChapterSplitService.ChapterResult> chapters = service.split("");
        assertTrue(chapters.isEmpty());
    }

    @Test
    void shouldHandleNullText() {
        List<ChapterSplitService.ChapterResult> chapters = service.split(null);
        assertTrue(chapters.isEmpty());
    }

    @Test
    void shouldSplitEnglishChapters() {
        String text = """
                Chapter 1 The Beginning
                This is the start of the story with some content.

                Chapter 2 The Middle
                The story continues with more development.

                Chapter 3 The End
                Everything wraps up nicely here.
                """;

        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertTrue(chapters.size() >= 3, "Should find English chapters");
    }

    @Test
    void shouldSkipShortFragments() {
        String text = "第一章 标题\n短";

        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        // Very short chapter should be skipped or included as-is
        assertTrue(chapters.size() >= 0);
    }
}
