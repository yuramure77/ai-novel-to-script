package com.scripttool.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("分章服务 — 7级策略")
class ChapterSplitServiceTest {

    private final ChapterSplitService service = new ChapterSplitService();

    String lorem = "这是一段用于填充章节内容的长文本它包含了丰富的描写和细节确保每个章节之间有足够的字符数量来通过分章算法的最小间距验证";

    @Test
    @DisplayName("中文第X章识别")
    void chineseChapters() {
        String ch1 = lorem.repeat(3) + "松枝清显站在窗前望着纷飞的雪花心中涌起难以名状的情愫";
        String ch2 = lorem.repeat(3) + "本多繁邦推门进来带着一股寒气眼镜片上蒙着薄薄的水雾";
        String text = "第一章 春雪\n\n" + ch1 + "\n\n第二章 青梅竹马\n\n" + ch2;
        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertTrue(chapters.size() >= 2, "应识别2章, 实际:" + chapters.size());
    }

    @Test
    @DisplayName("第X部/篇识别")
    void sectionMarkers() {
        String p1 = lorem.repeat(3) + "明治四十五年的春天松枝清显还是一个十二岁的少年";
        String p2 = lorem.repeat(3) + "十六岁的清显已经长成了让所有人惊叹的美少年";
        String text = "第一部 少年时代\n\n" + p1 + "\n\n第二部 青春之门\n\n" + p2;
        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertTrue(chapters.size() >= 2, "应识别2部, 实际:" + chapters.size());
    }

    @Test
    @DisplayName("fallback无章节长文本自动分块")
    void fallbackLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++)
            sb.append("小说内容段落").append(i+1).append("。").append(lorem).append("\n\n");
        List<ChapterSplitService.ChapterResult> chapters = service.split(sb.toString());
        assertTrue(chapters.size() >= 3, "长文本应分块, 实际:" + chapters.size());
    }

    @Test
    @DisplayName("空文本/null安全")
    void nullSafe() {
        assertNotNull(service.split(""));
        assertNotNull(service.split(null));
    }

    @Test
    @DisplayName("每章标题/内容非空")
    void chaptersHaveTitle() {
        String c = lorem.repeat(5);
        String text = "第一章 开端\n\n" + c + "\n\n第二章 展开\n\n" + c + "\n\n第三章 结局\n\n" + c;
        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertTrue(chapters.size() >= 3, "应识别3章, 实际:" + chapters.size());
        chapters.forEach(ch -> {
            assertNotNull(ch.title());
            assertFalse(ch.content().isBlank());
            assertTrue(ch.content().length() >= 50);
        });
    }
}
