package com.scripttool.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChapterSplitService {

    // ── Pattern tier list: most specific → least specific ──
    // Prevent false positives by ordering patterns from strictest to loosest

    /** 第X章 / 第X回 — gold standard, near-zero false positives */
    private static final Pattern CH_MARKER = Pattern.compile(
            "^[ \\t]*(第[一二三四五六七八九十百千万零\\d]+[章回][^\\n]*)", Pattern.MULTILINE
    );

    /** Chapter 1, CHAPTER TWO — English novels */
    private static final Pattern EN_MARKER = Pattern.compile(
            "^(Chapter\\s+[\\dIVX]+[^\\n]*)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    /** 第X部/篇/卷/节/集 — broader Chinese sections */
    private static final Pattern SEC_MARKER = Pattern.compile(
            "^[ \\t]*(第[一二三四五六七八九十百千万零\\d]+[部篇卷节集][^\\n]*)", Pattern.MULTILINE
    );

    /** Roman numerals on own line: I. II. III. IV. V. ... requires ≥3 to be valid */
    private static final Pattern ROMAN_MARKER = Pattern.compile(
            "^[ \\t]*(M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3}))[.、。]?\\s*$",
            Pattern.MULTILINE
    );

    /** Standalone Chinese/Japanese numbers on very short lines (≤6 chars): 一 / 二 / 三
     *  Only valid if ≥3 are found in sequence to avoid matching stray characters */
    private static final Pattern JP_SHORT_MARKER = Pattern.compile(
            "^[ \\t]*([一二三四五六七八九十]{1,2})\\s*$", Pattern.MULTILINE
    );

    /** Number+punctuation at line start: 1. / 1) / 1、 — requires ≥3 sequential */
    private static final Pattern NUM_DOT_MARKER = Pattern.compile(
            "^(\\d{1,3})[.、。．,，)）]\\s", Pattern.MULTILINE
    );

    /** Separator lines: ***, ---, ___ (3+ repeated chars on a short line) */
    private static final Pattern SEP_MARKER = Pattern.compile(
            "^[ \\t]*(\\*{3,}|-{3,}|_{3,}|~{3,}|#{3,})\\s*$", Pattern.MULTILINE
    );

    // ── Public API ──

    public List<ChapterResult> split(String fullText) {
        if (fullText == null || fullText.isBlank()) return List.of();
        String text = fullText.replace("\r\n", "\n").trim();

        List<ChapterResult> chapters;

        // Tier 1: Chinese 第X章
        chapters = splitByMarkers(text, CH_MARKER, false);

        // Tier 2: English Chapter X
        if (chapters.size() < 3) chapters = new ArrayList<>(splitByMarkers(text, EN_MARKER, false));

        // Tier 3: 第X部/篇/卷/节
        if (chapters.size() < 3) chapters = new ArrayList<>(splitByMarkers(text, SEC_MARKER, false));

        // Tier 4: Roman numerals (validated: ≥3 sequential)
        if (chapters.size() < 3) chapters = new ArrayList<>(splitByMarkers(text, ROMAN_MARKER, true));

        // Tier 5: Japanese standalone numbers (validated: ≥3, short lines, sequential)
        if (chapters.size() < 3) chapters = new ArrayList<>(splitJapaneseMarkers(text));

        // Tier 6: Numbered dots (validated: ≥3 sequential)
        if (chapters.size() < 3) chapters = new ArrayList<>(splitByMarkers(text, NUM_DOT_MARKER, true));

        // Tier 7: Separator lines as chapter boundaries
        if (chapters.size() < 3) chapters = new ArrayList<>(splitByMarkers(text, SEP_MARKER, false));

        // Fallback: paragraph-based smart chunking
        if (chapters.size() < 3 && text.length() > 3000) {
            chapters = smartChunk(text);
        }

        if (chapters.isEmpty()) chapters.add(new ChapterResult(1, "全文", text));
        return chapters;
    }

    // ── Core splitter ──

    private List<ChapterResult> splitByMarkers(String text, Pattern pattern, boolean requireSequence) {
        List<Integer> positions = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            positions.add(m.start());
            titles.add(m.group(1).trim());
        }

        if (positions.isEmpty()) return new ArrayList<>();
        if (positions.size() < 3 && requireSequence) return new ArrayList<>();

        // Validate: markers should be roughly sequential and not inside body text
        if (requireSequence && !isValidChapterSequence(positions, text)) {
            return new ArrayList<>();
        }

        List<ChapterResult> chapters = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            int start = positions.get(i);
            int end = (i + 1 < positions.size()) ? positions.get(i + 1) : text.length();
            String content = text.substring(start, end).trim();
            if (content.length() >= 50) { // Minimum 50 chars to avoid tiny fragments
                chapters.add(new ChapterResult(i + 1, titles.get(i), content));
            }
        }
        return chapters;
    }

    // ── Japanese standalone number handler ──

    /** Matches lines like "一" / "二" / "三" that appear alone.
     *  Only valid if ≥3 found and they appear at roughly regular intervals. */
    private List<ChapterResult> splitJapaneseMarkers(String text) {
        Matcher m = JP_SHORT_MARKER.matcher(text);
        List<Integer> positions = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        while (m.find()) {
            // Extra guard: the line before/after should be empty or short
            int lineStart = text.lastIndexOf('\n', m.start() - 1);
            int lineEnd = text.indexOf('\n', m.end());
            String prevLine = lineStart > 0 ? text.substring(
                    text.lastIndexOf('\n', lineStart - 1) + 1, lineStart).trim() : "";
            String nextLine = lineEnd > 0 ? text.substring(m.end() + 1,
                    text.indexOf('\n', lineEnd) > 0 ? text.indexOf('\n', lineEnd) : text.length()).trim() : "";

            // The marker should be surrounded by whitespace (blank lines) or be at document start
            boolean prevBlank = prevLine.isEmpty() || lineStart <= 0;
            boolean nextHasContent = !nextLine.isEmpty() && nextLine.length() > 20;

            if (prevBlank && nextHasContent) {
                positions.add(m.start());
                titles.add("第" + m.group(1).trim() + "章");
            }
        }

        if (positions.size() < 3) return new ArrayList<>();
        if (!isValidChapterSequence(positions, text)) return new ArrayList<>();

        return buildChapters(text, positions, titles);
    }

    // ── Validation ──

    /** Check that markers represent real chapter divisions, not body text matches.
     *  Real chapters: markers are roughly evenly spaced, each followed by substantial text. */
    private boolean isValidChapterSequence(List<Integer> positions, String text) {
        if (positions.size() < 3) return true; // Can't validate with too few

        // Calculate gap sizes between markers
        List<Integer> gaps = new ArrayList<>();
        for (int i = 0; i < positions.size() - 1; i++) {
            gaps.add(positions.get(i + 1) - positions.get(i));
        }

        // Check: each gap should be at least 100 chars (real chapters have content)
        long tooSmallGaps = gaps.stream().filter(g -> g < 100).count();
        if (tooSmallGaps > gaps.size() / 2) return false;

        // Check: gaps should be roughly similar (not random scatter)
        // Median gap should be within 5x of the smallest gap
        List<Integer> sorted = gaps.stream().sorted().toList();
        int median = sorted.get(sorted.size() / 2);
        int min = sorted.get(0);
        if (median > min * 5 && min < 500) return false;

        return true;
    }

    // ── Chapter builders ──

    private List<ChapterResult> buildChapters(String text, List<Integer> positions, List<String> titles) {
        List<ChapterResult> chapters = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            int start = positions.get(i);
            int end = (i + 1 < positions.size()) ? positions.get(i + 1) : text.length();
            if (end <= start) continue; // Skip out-of-order markers
            String content = text.substring(start, end).trim();
            if (content.length() >= 50) {
                chapters.add(new ChapterResult(i + 1, titles.get(i), content));
            }
        }
        return chapters;
    }

    // ── Fallback: paragraph-based smart chunk ──

    /** Group paragraphs into ~5-10 chapters using double-newline boundaries. */
    private List<ChapterResult> smartChunk(String text) {
        String[] paragraphs = text.split("\n\n");

        // If we have very few paragraphs, use size-based fallback
        if (paragraphs.length < 10) {
            return chunkByTargetCount(text, Math.min(8, Math.max(3, text.length() / 12000)));
        }

        int targetChapters = Math.min(8, Math.max(4, paragraphs.length / 20));
        List<ChapterResult> chapters = new ArrayList<>();
        int parasPerChapter = Math.max(1, paragraphs.length / targetChapters);
        int chapterNum = 1, paraIdx = 0;

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
                chapters.add(new ChapterResult(chapterNum++, "第" + chapterNum + "部分", chapterText));
            }
            paraIdx = end;
        }
        return chapters;
    }

    private List<ChapterResult> chunkByTargetCount(String text, int targetCount) {
        int totalLen = text.length();
        int chunkSize = Math.max(2000, totalLen / targetCount);
        List<ChapterResult> chapters = new ArrayList<>();
        int start = 0, num = 1;
        while (start < totalLen && num <= 20) {
            int end = Math.min(start + chunkSize, totalLen);
            if (end < totalLen) {
                int brk = text.lastIndexOf("\n\n", end);
                if (brk < start + chunkSize / 2) brk = text.lastIndexOf("\n", end);
                if (brk > start + chunkSize / 2) end = brk;
            }
            String content = text.substring(start, end).trim();
            if (content.length() >= 100) {
                chapters.add(new ChapterResult(num++, "第" + (num - 1) + "部分", content));
            }
            start = end;
        }
        return chapters;
    }

    public record ChapterResult(int number, String title, String content) {
        public String toSummary() {
            return String.format("第%d章: %s (%d字)", number, title, content.length());
        }
    }
}
