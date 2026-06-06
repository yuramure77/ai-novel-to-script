package com.scripttool.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChapterSplitService {

    // Chinese chapter markers: 第X章, 第一章, 第 一 章
    private static final Pattern CH_MARKER = Pattern.compile(
            "^[ \\t]*(第[一二三四五六七八九十百千万零\\d]+章[^\\n]*)", Pattern.MULTILINE
    );
    // English markers: Chapter 1, CHAPTER ONE
    private static final Pattern EN_MARKER = Pattern.compile(
            "^(Chapter\\s+\\d+[^\\n]*)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );
    // Japanese/Alternative markers: 一, 二, 三, 四, 五, 六, 七, 八, 九, 十 (standalone on short lines)
    // Also matches: 第一部分, 第二部, 上篇, 下篇, 卷一
    private static final Pattern ALT_MARKER = Pattern.compile(
            "^[ \\t]*([一二三四五六七八九十]+[、。，．.,\\s]|[第]?[一二三四五六七八九十]+[部篇卷节回])[^\\n]{0,30}$", Pattern.MULTILINE
    );
    // Numbered sections: 1., 1), Section 1
    private static final Pattern NUM_MARKER = Pattern.compile(
            "^(Section\\s+\\d+|Part\\s+\\d+)[^\\n]*", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    public List<ChapterResult> split(String fullText) {
        if (fullText == null || fullText.isBlank()) return List.of();
        String text = fullText.replace("\r\n", "\n").trim();

        // Try Chinese markers first
        List<ChapterResult> chapters = splitByMarkers(text, CH_MARKER);
        // If not enough, try English
        if (chapters.size() < 3) chapters = new ArrayList<>(splitByMarkers(text, EN_MARKER));
        // Try alternative markers (Japanese/classical Chinese)
        if (chapters.size() < 3) chapters = new ArrayList<>(splitByMarkers(text, ALT_MARKER));
        // Try numbered section markers
        if (chapters.size() < 3) chapters = new ArrayList<>(splitByMarkers(text, NUM_MARKER));

        // If still no good split, use paragraph-based smart chunking
        if (chapters.size() < 3 && text.length() > 3000) {
            chapters = smartChunk(text);
        }
        if (chapters.isEmpty()) chapters.add(new ChapterResult(1, "全文", text));
        return chapters;
    }

    /**
     * Smart chunking: group paragraphs into chapters.
     * Targets 5-10 chapters regardless of text length.
     * Uses double-newlines as natural paragraph boundaries.
     */
    private List<ChapterResult> smartChunk(String text) {
        // Split into paragraphs
        String[] paragraphs = text.split("\n\n");
        if (paragraphs.length < 10) {
            // Too few paragraphs, just wrap as-is or split by another heuristic
            return chunkByTargetCount(text, 6);
        }

        // Group paragraphs into target number of chapters
        int targetChapters = Math.min(10, Math.max(5, paragraphs.length / 15));
        List<ChapterResult> chapters = new ArrayList<>();
        int parasPerChapter = Math.max(1, paragraphs.length / targetChapters);
        int chapterNum = 1;
        int paraIdx = 0;

        while (paraIdx < paragraphs.length) {
            int end = Math.min(paraIdx + parasPerChapter, paragraphs.length);
            StringBuilder content = new StringBuilder();
            for (int i = paraIdx; i < end; i++) {
                if (!paragraphs[i].isBlank()) {
                    content.append(paragraphs[i].trim()).append("\n\n");
                }
            }
            String chapterText = content.toString().trim();
            if (chapterText.length() >= 50) {
                chapters.add(new ChapterResult(chapterNum++, "第" + (chapterNum - 1) + "章", chapterText));
            }
            paraIdx = end;
        }
        return chapters;
    }

    /** Fallback: split text into exactly targetCount chunks of roughly equal size */
    private List<ChapterResult> chunkByTargetCount(String text, int targetCount) {
        int totalLen = text.length();
        int chunkSize = Math.max(2000, totalLen / targetCount);
        List<ChapterResult> chapters = new ArrayList<>();
        int start = 0, num = 1;
        while (start < totalLen && num <= 20) {
            int end = Math.min(start + chunkSize, totalLen);
            // Find best break point
            if (end < totalLen) {
                int brk = text.lastIndexOf("\n\n", end);
                if (brk < start + chunkSize / 2) {
                    brk = text.lastIndexOf("\n", end);
                }
                if (brk > start + chunkSize / 2) end = brk;
            }
            String content = text.substring(start, end).trim();
            if (content.length() >= 100) {
                chapters.add(new ChapterResult(num++, "第" + (num - 1) + "章", content));
            }
            start = end;
        }
        return chapters;
    }

    private List<ChapterResult> splitByMarkers(String text, Pattern markerPattern) {
        // Find all marker positions
        List<Integer> positions = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        Matcher m = markerPattern.matcher(text);
        while (m.find()) {
            positions.add(m.start());
            titles.add(m.group(1).trim());
        }
        if (positions.isEmpty()) return new ArrayList<>();

        List<ChapterResult> chapters = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            int start = positions.get(i);
            int end = (i + 1 < positions.size()) ? positions.get(i + 1) : text.length();
            String content = text.substring(start, end).trim();
            if (content.length() >= 20) {
                chapters.add(new ChapterResult(i + 1, titles.get(i), content));
            }
        }
        return chapters;
    }

    public record ChapterResult(int number, String title, String content) {
        public String toSummary() {
            return String.format("第%d章: %s (%d字)", number, title, content.length());
        }
    }
}
