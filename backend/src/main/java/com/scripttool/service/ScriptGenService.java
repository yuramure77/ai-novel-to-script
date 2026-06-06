package com.scripttool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripttool.config.DeepSeekConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ScriptGenService {

    private static final Logger log = LoggerFactory.getLogger(ScriptGenService.class);
    private static final int MAX_RETRIES = 2;
    private static final int MAX_CONCURRENT = 8;       // Max parallel AI calls — more=faster
    private static final int CHUNK_SIZE = 6000;        // Smaller chunks = faster individual responses
    private static final int CHUNK_OVERLAP = 200;
    private static final int MAX_TOTAL_TEXT = 500_000;
    private static final int MIN_CHUNK_FOR_SPLIT = 8000; // Only split chapters >8K chars

    private final DeepSeekConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT);

    private static final String SYSTEM_PROMPT = """
你是一个专业的剧本改编助手。你需要将小说文本转换为结构化的剧本格式。

转换规则：
1. 角色的 name 使用原文中的角色名
2. role 取值为: protagonist(主角), antagonist(反派), supporting(配角), minor(次要)
3. scene type 取值为: INT(室内) / EXT(室外) / INT/EXT(内外交替)，说明场景拍摄环境
4. beat type 取值为: action(动作/场景描写), dialogue(对白), monologue(独白/内心), narration(旁白), transition(转场)
5. character 字段仅在 beat type 为 dialogue 或 monologue 时需要填写
6. direction 是表演指导/舞台说明，用中文写
7. emotion 是该 beat 的角色情绪
8. 保留原文的关键对白和重要情节
9. 根据上下文推测场景地点和时间，无法判断时标注"未知"
10. 务必返回有效的 JSON，不要包含 markdown 代码块标记

严格按以下 JSON Schema 返回：
{
  "characters": [...],
  "scenes": [...]
}""";

    public ScriptGenService(DeepSeekConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Callback for progress updates during incremental generation.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int chapterNum, int totalChapters, int sceneCount, String message);
    }

    /**
     * Callback invoked after EACH chapter, with accumulated results so far.
     * This enables the frontend to show partial results immediately.
     */
    @FunctionalInterface
    public interface ChapterCallback {
        void onChapterDone(int chapterNum, int totalChapters, List<Map<String, Object>> chars,
                           List<Map<String, Object>> scenesSoFar, boolean isLast);
        /** Called per-chunk when a chapter has multiple chunks — gives faster first response */
        default void onChunkDone(int chapterNum, int totalChapters, List<Map<String, Object>> chars,
                                 List<Map<String, Object>> scenesSoFar, boolean isFirstChapter) {}
    }

    public ScriptResult generateScript(String fullText, List<ChapterSplitService.ChapterResult> chapters) {
        return generateScriptIncremental(fullText, chapters, null, null);
    }

    public ScriptResult generateScriptIncremental(String fullText,
            List<ChapterSplitService.ChapterResult> chapters, ProgressCallback callback) {
        return generateScriptIncremental(fullText, chapters, callback, null);
    }

    public ScriptResult generateScriptIncremental(String fullText,
            List<ChapterSplitService.ChapterResult> chapters, ProgressCallback progressCb,
            ChapterCallback chapterCb) {
        // Guard: refuse extremely long texts
        if (fullText.length() > MAX_TOTAL_TEXT) {
            throw new RuntimeException(String.format(
                    "文本过长（%d字），请缩短至50万字以内或分段上传", fullText.length()));
        }
        // Limit to 15 chapters max
        List<ChapterSplitService.ChapterResult> limited = chapters;
        if (chapters.size() > 15) {
            log.warn("Too many chapters ({}), limiting to first 15", chapters.size());
            limited = chapters.subList(0, 15);
        }

        // Skip separate character extraction — saves 10s at startup.
        // Characters are parsed from YAML by the frontend; AI generates them inline.
        List<Map<String, Object>> allCharacters = List.of();

        int total = limited.size();

        // Flatten ALL chapters into individual chunks for maximum parallelism
        record ChunkTask(int chapterNum, int chunkIdx, int totalChapters, String text) {}
        List<ChunkTask> allChunks = new ArrayList<>();
        for (ChapterSplitService.ChapterResult chapter : limited) {
            if (chapter.content().length() <= MIN_CHUNK_FOR_SPLIT) {
                allChunks.add(new ChunkTask(chapter.number(), 1, total, chapter.content()));
            } else {
                List<String> parts = splitIntoChunks(chapter.content());
                for (int j = 0; j < parts.size(); j++) {
                    allChunks.add(new ChunkTask(chapter.number(), j + 1, total, parts.get(j)));
                }
            }
        }
        log.info("Total {} chunks across {} chapters — processing in parallel (max {})",
                allChunks.size(), total, MAX_CONCURRENT);

        // Process ALL chunks in one parallel pool — first-come-first-displayed
        List<Map<String, Object>> allScenes = Collections.synchronizedList(new ArrayList<>());
        Map<Integer, AtomicInteger> chapterDoneCount = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ChunkTask task : allChunks) {
            futures.add(CompletableFuture.runAsync(() -> {
                List<Map<String, Object>> scenes = generateScenesSingle(
                        task.text(), task.chapterNum(), allCharacters, task.chunkIdx());
                allScenes.addAll(scenes);

                int done = chapterDoneCount.computeIfAbsent(task.chapterNum(),
                        k -> new AtomicInteger(0)).incrementAndGet();

                // Fire callback immediately after each chunk for real-time display
                if (chapterCb != null) {
                    List<Map<String, Object>> soFar = new ArrayList<>(allScenes);
                    soFar.sort((a, b) -> {
                        int ca = ((Number) a.getOrDefault("chapter", 0)).intValue();
                        int cb = ((Number) b.getOrDefault("chapter", 0)).intValue();
                        return ca != cb ? ca - cb :
                            ((Number) a.getOrDefault("scene_number", 0)).intValue() -
                            ((Number) b.getOrDefault("scene_number", 0)).intValue();
                    });
                    chapterCb.onChunkDone(task.chapterNum(), total, allCharacters, soFar,
                            task.chapterNum() == 1);
                }
                if (progressCb != null) {
                    long totalDone = chapterDoneCount.values().stream()
                            .mapToInt(AtomicInteger::get).sum();
                    progressCb.onProgress((int) totalDone, allChunks.size(),
                            scenes.size(), totalDone + "/" + allChunks.size() + " 块完成");
                }
            }, executor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Generation failed", e);
            throw new RuntimeException("生成失败: " + e.getMessage(), e);
        }

        // Final sort + callback
        allScenes.sort((a, b) -> {
            int ca = ((Number) a.getOrDefault("chapter", 0)).intValue();
            int cb = ((Number) b.getOrDefault("chapter", 0)).intValue();
            return ca != cb ? ca - cb :
                ((Number) a.getOrDefault("scene_number", 0)).intValue() -
                ((Number) b.getOrDefault("scene_number", 0)).intValue();
        });
        if (chapterCb != null) {
            chapterCb.onChapterDone(total, total, allCharacters, allScenes, true);
        }

        return new ScriptResult(allCharacters, allScenes);
    }

    /**
     * Generate scenes for one chapter. If the chapter is long, split into
     * overlapping chunks, process in parallel, then merge.
     */
    private List<Map<String, Object>> generateChapterScenes(
            String text, int chapterNum, List<Map<String, Object>> chars,
            ChapterCallback callback, int totalChapters) {

        // If text is short enough, process as a single chunk (faster)
        if (text.length() <= MIN_CHUNK_FOR_SPLIT) {
            return generateScenesSingle(text, chapterNum, chars, 1);
        }

        // Split into overlapping chunks
        List<String> chunks = splitIntoChunks(text);
        log.info("Chapter {} split into {} chunks ({} chars total)", chapterNum, chunks.size(), text.length());

        // Process chunks in parallel, fire callback on each completion
        List<Map<String, Object>> allScenes = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> chunkFutures = new ArrayList<>();
        boolean isFirstChapter = (chapterNum == 1);

        for (int i = 0; i < chunks.size(); i++) {
            final int idx = i;
            final String chunk = chunks.get(i);
            chunkFutures.add(CompletableFuture.runAsync(() -> {
                List<Map<String, Object>> scenes = generateScenesSingle(
                        chunk, chapterNum, chars, idx + 1);
                allScenes.addAll(scenes);
                // Fire callback immediately after each chunk completes (first chapter only, for speed)
                if (isFirstChapter && callback != null) {
                    List<Map<String, Object>> soFar = new ArrayList<>(allScenes);
                    soFar.sort((a, b) -> {
                        int ca = ((Number) a.getOrDefault("chapter", 0)).intValue();
                        int cb = ((Number) b.getOrDefault("chapter", 0)).intValue();
                        return ca != cb ? ca - cb :
                            ((Number) a.getOrDefault("scene_number", 0)).intValue() -
                            ((Number) b.getOrDefault("scene_number", 0)).intValue();
                    });
                    callback.onChunkDone(chapterNum, totalChapters, chars, soFar, true);
                }
            }, executor));
        }

        try {
            CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                    .get(3, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Chapter {} chunk processing failed", chapterNum, e);
        }

        // Merge and deduplicate scenes
        List<Map<String, Object>> merged = mergeAndDeduplicate(allScenes);
        // Renumber scenes within chapter
        for (int i = 0; i < merged.size(); i++) {
            merged.get(i).put("scene_number", i + 1);
            merged.get(i).put("chapter", chapterNum);
        }
        return merged;
    }

    /**
     * Split text into overlapping chunks for parallel processing
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            // Try to break at sentence/paragraph boundary
            if (end < text.length()) {
                int brk = findBreakPoint(text, end);
                if (brk > start + CHUNK_SIZE / 2) end = brk;
            }
            chunks.add(text.substring(start, end).trim());
            start = end - CHUNK_OVERLAP;
            if (start >= text.length() - 100) break;
        }
        return chunks;
    }

    private int findBreakPoint(String text, int around) {
        // Look for paragraph break, sentence end, or newline near the target
        for (int d = 200; d >= 0; d--) {
            int pos = around - d;
            if (pos > 0 && pos < text.length()) {
                char c = text.charAt(pos);
                if (c == '\n' && pos + 1 < text.length() && text.charAt(pos + 1) == '\n') return pos;
                if (c == '。' || c == '！' || c == '？' || c == '"' || c == '」') return pos + 1;
            }
        }
        return around;
    }

    /**
     * Generate scenes from a single chunk of text
     */
    private List<Map<String, Object>> generateScenesSingle(
            String text, int chapterNum, List<Map<String, Object>> chars, int chunkIdx) {
        String hint = chars.isEmpty() ? "暂无" : chars.stream()
                .map(c -> c.get("name").toString())
                .limit(20) // Limit character list to keep prompt concise
                .reduce((a, b) -> a + ", " + b).orElse("暂无");
        String prompt = String.format(
                "请将以下小说文本转换为剧本场景（第%d章第%d部分）\n\n已知角色：%s\n\n原文：%s",
                chapterNum, chunkIdx, hint, text);
        String response = callWithRetry(prompt);
        Map<String, Object> result = parseJsonResponse(response);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenes = (List<Map<String, Object>>) result.getOrDefault("scenes", List.of());
        for (Map<String, Object> scene : scenes) {
            scene.putIfAbsent("chapter", chapterNum);
        }
        return scenes;
    }

    /**
     * Merge scenes from multiple chunks: deduplicate by title/content similarity
     */
    private List<Map<String, Object>> mergeAndDeduplicate(List<Map<String, Object>> scenes) {
        if (scenes.size() <= 1) return new ArrayList<>(scenes);

        List<Map<String, Object>> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Map<String, Object> scene : scenes) {
            String title = Objects.toString(scene.getOrDefault("description", ""), "");
            String loc = Objects.toString(scene.getOrDefault("location", ""), "");
            String key = (title + "|" + loc).trim();
            // Skip if very similar scene already exists
            if (!key.isBlank() && !seen.add(key)) continue;
            merged.add(scene);
        }
        return merged;
    }

    /**
     * Extract characters from sample text
     */
    private List<Map<String, Object>> extractCharacters(String text, int chapterNum) {
        String prompt = String.format(
                "请提取以下小说文本中的全部角色信息：\n\n%s\n\n请返回所有角色，包括次要角色。", text);
        String response = callWithRetry(prompt);
        Map<String, Object> result = parseJsonResponse(response);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chars = (List<Map<String, Object>>) result.getOrDefault("characters", List.of());
        // Deduplicate by name
        Set<String> names = new HashSet<>();
        return chars.stream()
                .filter(c -> names.add(Objects.toString(c.get("name"), "")))
                .collect(Collectors.toList());
    }

    // ============ API communication ============

    private String callWithRetry(String userPrompt) {
        Exception lastEx = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("Retry {} for DeepSeek API", attempt);
                    Thread.sleep(2000L * attempt);
                }
                return callDeepSeek(userPrompt);
            } catch (RestClientException e) {
                lastEx = e;
                log.warn("API call failed (attempt {}): {}", attempt + 1, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", e);
            }
        }
        throw new RuntimeException("AI 调用失败，已重试 " + MAX_RETRIES + " 次", lastEx);
    }

    private String callDeepSeek(String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", config.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "max_tokens", config.getMaxTokens(),
                "temperature", config.getTemperature(),
                "response_format", Map.of("type", "json_object")
        );

        HttpHeaders headers = config.createHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String response = restTemplate.postForObject(config.getApiUrl(), entity, String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse API response", e);
        }
    }

    private Map<String, Object> parseJsonResponse(String jsonStr) {
        try {
            String cleaned = jsonStr.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf('\n'), end = cleaned.lastIndexOf("```");
                if (start > 0 && end > start) cleaned = cleaned.substring(start + 1, end).trim();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(cleaned, Map.class);
            return result;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse AI response: " +
                    jsonStr.substring(0, Math.min(200, jsonStr.length())), e);
        }
    }

    public record ScriptResult(List<Map<String, Object>> characters, List<Map<String, Object>> scenes) {}
}
