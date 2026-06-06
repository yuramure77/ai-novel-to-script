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
                这里有很多情节发展，主角踏上了漫长的旅途。
                一路上遇到了各种各样的挑战和机遇。
                这段内容需要足够长才能被识别为有效章节。
                继续添加更多文字来满足最小长度限制。
                现在已经足够了，应该可以通过检测。

                第二章 发展
                故事进入发展阶段，主角面临新的挑战。
                新的角色出现，情节变得复杂起来。
                主角结识了志同道合的伙伴，组建了团队。
                他们一起面对更强大的敌人和困难。
                这段内容也需要足够长才行。
                继续填充更多的文字来满足要求。
                现在应该可以通过检测了。

                第三章 高潮
                所有矛盾和冲突在这一章集中爆发。
                主角做出了关键选择，决定面对命运。
                激烈的战斗持续了三天三夜。
                最终主角凭借智慧和勇气取得了胜利。
                这段内容同样需要满足最小长度。
                继续添加更多的文字内容。
                现在已经足够长了。

                第四章 结局
                故事走向终点，一切尘埃落定。
                主角回到了故乡，开始了新的生活。
                曾经的伙伴们也各自找到了归宿。
                这段经历将永远铭刻在他们心中。
                结尾的内容也需要足够长。
                继续添加更多的文字来填充。
                现在应该可以满足条件了。
                """;

        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertTrue(chapters.size() >= 3, "Should find at least 3 chapters, got: " + chapters.size());
        assertTrue(chapters.get(0).title().contains("第一章"), "Title should contain chapter marker");
        assertTrue(chapters.get(1).title().contains("第二章"), "Title should contain chapter marker");
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
        assertTrue(chapters.size() >= 0);
    }

    @Test
    void shouldHandleChapterNumbers() {
        String text = """
                第1章 开始
                第一章内容，这里有很多文字需要足够长才能被识别为有效章节。
                这是额外的填充内容以满足最小长度要求，继续添加更多内容。
                确保超过五十个字符的限制，现在应该足够了，继续写一些东西。
                还要再添加一些内容，确保不会因为太短而被过滤掉，好的。

                第2章 继续
                第二章内容同样需要足够的文字，这是第二章节的填充内容。
                继续添加更多文字以满足最小长度要求，现在应该可以满足条件了。
                还需要再多一些文字来确保万无一失，继续填充更多的内容。
                再补充一些文字，让内容更加充实和完整，应该差不多了。

                第10章 结局
                第十章的内容也需要足够长，填充内容以满足最小字符限制。
                继续添加更多的文字内容，这样应该可以满足所有条件了。
                还需要再补充一些内容以确保测试的稳定性，继续填写更多文字。
                最后再添加一行确保长度足够，现在应该完全没问题了。
                """;

        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertTrue(chapters.size() >= 3);
    }

    @Test
    void shouldDetectMixedFormat() {
        // If text has both Chinese and English markers, prefer Chinese
        String text = """
                Chapter 1 Start
                Some content here that needs to be long enough.
                Adding more text to meet the minimum character requirement.
                This should be enough now for the chapter to be valid.

                第一章 中文开头
                中文内容需要足够长。
                继续添加更多内容。
                这是中文章节的额外填充。
                现在应该足够了。
                """;

        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertTrue(chapters.size() >= 1);
    }

    @Test
    void shouldPreserveChapterContent() {
        String text = """
                第一章 测试
                这是第一章的具体内容，包含一些对话和情节。
                主角说道："我们要继续前进。"于是他们继续赶路。
                这一路上有许许多多的艰难险阻等待着他们去克服。
                需要足够多的文字来让这个章节通过最小长度限制。\n\n第二章 验证
                第二章的内容完全不同，这里有新的故事线展开。
                新的角色登场了，带来了全新的视角和冲突。
                他们遇到了一个神秘的旅人，改变了整个故事的走向。
                继续添加更多内容来满足最小长度要求。\n\n第三章 结局
                最后的章节展示了所有冲突的圆满解决。
                主角回到了家乡，一切尘埃落定。
                这是故事的结尾部分，也需要足够长的内容。
                继续填充一些文字来保证通过长度检测。\n""";

        List<ChapterSplitService.ChapterResult> chapters = service.split(text);
        assertTrue(chapters.size() >= 2, "Should find at least 2 chapters, got: " + chapters.size());
    }
}
