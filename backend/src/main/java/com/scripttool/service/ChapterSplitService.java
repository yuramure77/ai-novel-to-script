package com.scripttool.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChapterSplitService {

    // Match Chinese chapter markers: 第一章, 第1章, Chapter 1, etc.
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "(第[一二三四五六七八九十百千万零\\d]+章\\s*.*?)(?=第[一二三四五六七八九十百千万零\\d]+章|$)",
            Pattern.DOTALL
    );

    // Fallback: match "Chapter N" style
    private static final Pattern CHAPTER_PATTERN_EN = Pattern.compile(
            "(Chapter\\s+\\d+[^\\n]*)(?=Chapter\\s+\\d+|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    public List<ChapterResult> split(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return List.of();
        }

        String cleaned = fullText.replace("\r\n", "\n").trim();

        // Try Chinese chapter markers first
        List<ChapterResult> chapters = splitByPattern(cleaned, CHAPTER_PATTERN);
        if (chapters.size() >= 3) {
            return chapters;
        }

        // Try English chapter markers
        chapters = splitByPattern(cleaned, CHAPTER_PATTERN_EN);
        if (chapters.size() >= 3) {
            return chapters;
        }

        // Fallback: treat the entire text as one chapter
        ChapterResult single = new ChapterResult(1, "全文", cleaned);
        return List.of(single);
    }

    private List<ChapterResult> splitByPattern(String text, Pattern pattern) {
        List<ChapterResult> chapters = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);

        int index = 1;
        while (matcher.find()) {
            String content = matcher.group().trim();
            if (content.length() < 50) continue; // skip empty/fragment chapters

            String title = extractTitle(content);
            chapters.add(new ChapterResult(index++, title, content));
        }

        // If regex didn't match, check if text just starts with a chapter marker
        if (chapters.isEmpty()) {
            Matcher firstMatch = Pattern.compile(
                    "^\\s*(第[一二三四五六七八九十百千万零\\d]+章[^\\n]*|Chapter\\s+\\d+[^\\n]*)",
                    Pattern.CASE_INSENSITIVE
            ).matcher(text);

            if (firstMatch.find()) {
                // Manual split by chapter markers
                String[] parts = text.split(
                        "(?=第[一二三四五六七八九十百千万零\\d]+章|Chapter\\s+\\d+)",
                        -1
                );
                int idx = 1;
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (trimmed.length() >= 50) {
                        chapters.add(new ChapterResult(idx++, extractTitle(trimmed), trimmed));
                    }
                }
            }
        }

        return chapters;
    }

    private String extractTitle(String content) {
        // Extract first line as title
        int newlineIdx = content.indexOf('\n');
        if (newlineIdx > 0) {
            return content.substring(0, newlineIdx).trim();
        }
        // If no newline, use first 50 chars
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }

    public record ChapterResult(int number, String title, String content) {
        public String toSummary() {
            return String.format("第%d章: %s (%d字)", number, title, content.length());
        }
    }
}
