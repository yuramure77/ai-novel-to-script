package com.scripttool.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChapterSplitService {

    // Split on line-start chapter markers: ^第X章
    private static final Pattern CH_MARKER = Pattern.compile(
            "^(第[一二三四五六七八九十百千万零\\d]+章[^\\n]*)", Pattern.MULTILINE
    );
    private static final Pattern EN_MARKER = Pattern.compile(
            "^(Chapter\\s+\\d+[^\\n]*)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    public List<ChapterResult> split(String fullText) {
        if (fullText == null || fullText.isBlank()) return List.of();
        String text = fullText.replace("\r\n", "\n").trim();

        List<ChapterResult> chapters = splitByMarkers(text, CH_MARKER);
        if (chapters.size() < 3) chapters = new ArrayList<>(splitByMarkers(text, EN_MARKER));
        if (chapters.isEmpty()) chapters.add(new ChapterResult(1, "全文", text));
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
