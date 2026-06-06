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
import java.util.stream.Collectors;

@Service
public class ScriptGenService {

    private static final Logger log = LoggerFactory.getLogger(ScriptGenService.class);
    private static final int MAX_RETRIES = 2;
    private static final int CHUNK_SIZE = 3000;  // Characters per AI call

    private final DeepSeekConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
你是一个专业的剧本改编助手。将小说文本转换为结构化剧本格式。

转换规则：
1. 角色的 name 使用原文中的角色名
2. role 取值为: protagonist(主角), antagonist(反派), supporting(配角), minor(次要)
3. scene type 取值为: INT(室内) / EXT(室外) / INT/EXT(内外交替)
4. beat type 取值为: action(动作/场景描写), dialogue(对白), monologue(独白/内心), narration(旁白), transition(转场)
5. direction 用中文写表演指导
6. 保留原文关键对白和重要情节
7. 返回有效的 JSON，不要包含 markdown 代码块标记

返回格式:
{
  "characters": [{"name":"...", "role":"protagonist|supporting|minor"}],
  "scenes": [{"chapter":1, "scene_number":1, "type":"INT/EXT", "location":"...", "time":"...", "description":"...", "characters":["角色名"], "beats":[{"type":"dialogue", "character":"角色名", "line":"...", "direction":"...", "emotion":"..."}]}]
}""";

    public ScriptGenService(DeepSeekConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ─── Callbacks ───

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int done, int total, String message);
    }

    @FunctionalInterface
    public interface ChunkCallback {
        /** Called after each AI call completes with accumulated characters + scenes */
        void onChunk(List<Map<String, Object>> chars, List<Map<String, Object>> scenes, int done, int total);
    }

    // ─── Main entry ───

    public ScriptResult generate(String fullText, List<ChapterSplitService.ChapterResult> chapters,
                                  ProgressCallback onProgress, ChunkCallback onChunk) {
        if (fullText.length() > 500_000) {
            throw new RuntimeException("文本过长（" + fullText.length() + "字），请缩短至50万字以内");
        }

        List<Map<String, Object>> allScenes = new ArrayList<>();
        Set<String> charNames = new LinkedHashSet<>();
        List<Map<String, Object>> allCharacters = new ArrayList<>();

        // Build flat list of chunks from all chapters
        record Chunk(int chapter, int idx, int totalChunks, String text) {}
        List<Chunk> chunks = new ArrayList<>();
        for (var ch : chapters) {
            String text = ch.content();
            if (text.length() <= CHUNK_SIZE) {
                chunks.add(new Chunk(ch.number(), 1, -1, text));
            } else {
                List<String> parts = splitSimple(text, CHUNK_SIZE);
                for (int j = 0; j < parts.size(); j++) {
                    chunks.add(new Chunk(ch.number(), j + 1, parts.size(), parts.get(j)));
                }
            }
        }

        int total = chunks.size();
        log.info("{} chapters → {} chunks ({} chars each)", chapters.size(), total, CHUNK_SIZE);

        // Process ONE chunk at a time — simple, reliable, predictable
        for (int i = 0; i < total; i++) {
            Chunk c = chunks.get(i);
            if (onProgress != null) onProgress.onProgress(i + 1, total, "处理中 " + (i + 1) + "/" + total);

            String prompt = String.format(
                "将以下小说文本转换为剧本（第%d章%s）:\n\n%s",
                c.chapter, c.totalChunks > 1 ? "第" + c.idx + "/" + c.totalChunks + "部分" : "", c.text
            );

            String response = callWithRetry(prompt);
            Map<String, Object> result = parseJsonResponse(response);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chars = (List<Map<String, Object>>) result.getOrDefault("characters", List.of());
            for (Map<String, Object> ch : chars) {
                String name = Objects.toString(ch.get("name"), "");
                if (!name.isBlank() && charNames.add(name)) {
                    allCharacters.add(ch);
                }
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> scenes = (List<Map<String, Object>>) result.getOrDefault("scenes", List.of());
            for (Map<String, Object> s : scenes) {
                s.putIfAbsent("chapter", c.chapter);
                s.putIfAbsent("scene_number", allScenes.size() + 1);
            }
            allScenes.addAll(scenes);

            if (onChunk != null) onChunk.onChunk(allCharacters, allScenes, i + 1, total);
        }

        return new ScriptResult(allCharacters, allScenes);
    }

    // ─── Simple text splitter ───

    private List<String> splitSimple(String text, int size) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            if (end < text.length()) {
                // Try to break at sentence end
                int brk = text.lastIndexOf("。", end);
                if (brk > start + size / 2) end = brk + 1;
                else {
                    brk = text.lastIndexOf("\n", end);
                    if (brk > start + size / 2) end = brk;
                }
            }
            parts.add(text.substring(start, end).trim());
            start = end;
        }
        return parts;
    }

    // ─── API communication ───

    private String callWithRetry(String userPrompt) {
        Exception lastEx = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("Retry {}", attempt);
                    Thread.sleep(2000L * attempt);
                }
                return callDeepSeek(userPrompt);
            } catch (RestClientException e) {
                lastEx = e;
                log.warn("API attempt {} failed: {}", attempt + 1, e.getMessage());
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
