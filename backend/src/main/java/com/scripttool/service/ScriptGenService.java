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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ScriptGenService {

    private static final Logger log = LoggerFactory.getLogger(ScriptGenService.class);
    private static final int MAX_RETRIES = 2;
    private static final int PARALLEL_CHAPTERS = 3;

    private final DeepSeekConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_CHAPTERS);

    private static final String SYSTEM_PROMPT = """
你是一个专业的剧本改编助手。你需要将小说章节转换为结构化的剧本格式。

转换规则：
1. 角色的 name 使用原文中的角色名
2. role 取值为: protagonist(主角), antagonist(反派), supporting(配角), minor(次要)
3. scene type 取值为: INT(室内), EXT(室外), INT/EXT(内外交替)
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

    public ScriptResult generateScript(String fullText, List<ChapterSplitService.ChapterResult> chapters) {
        // Extract characters first (single API call)
        List<Map<String, Object>> allCharacters = extractCharacters(
                chapters.get(0).content(), chapters.get(0).number());

        // Process chapters in parallel
        List<Map<String, Object>> allScenes = new CopyOnWriteArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ChapterSplitService.ChapterResult chapter : chapters) {
            futures.add(CompletableFuture.runAsync(() -> {
                allScenes.addAll(generateScenes(chapter.content(), chapter.number(), allCharacters));
                log.info("Chapter {} done", chapter.number());
            }, executor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Processing failed", e);
            throw new RuntimeException("处理失败", e);
        }

        allScenes.sort((a, b) -> {
            int ca = ((Number) a.getOrDefault("chapter", 0)).intValue();
            int cb = ((Number) b.getOrDefault("chapter", 0)).intValue();
            return ca != cb ? ca - cb :
                ((Number) a.getOrDefault("scene_number", 0)).intValue() -
                ((Number) b.getOrDefault("scene_number", 0)).intValue();
        });

        return new ScriptResult(allCharacters, allScenes);
    }

    private List<Map<String, Object>> extractCharacters(String text, int chapterNum) {
        String prompt = String.format("请提取以下小说文本中的全部角色信息（第%d章）：\n\n%s", chapterNum, text);
        String response = callWithRetry(prompt);
        Map<String, Object> result = parseJsonResponse(response);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chars = (List<Map<String, Object>>) result.getOrDefault("characters", List.of());
        return chars;
    }

    private List<Map<String, Object>> generateScenes(String text, int chapterNum, List<Map<String, Object>> chars) {
        String hint = chars.isEmpty() ? "暂无" : chars.stream()
                .map(c -> c.get("name").toString())
                .reduce((a, b) -> a + ", " + b).orElse("暂无");
        String prompt = String.format(
                "请将以下小说章节转换为剧本场景（第%d章）\n\n已知角色：%s\n\n章节原文：%s",
                chapterNum, hint, text);
        String response = callWithRetry(prompt);
        Map<String, Object> result = parseJsonResponse(response);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenes = (List<Map<String, Object>>) result.getOrDefault("scenes", List.of());
        for (Map<String, Object> scene : scenes) {
            scene.putIfAbsent("chapter", chapterNum);
        }
        return scenes;
    }

    private String callWithRetry(String userPrompt) {
        Exception lastEx = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("Retry {} for DeepSeek API", attempt);
                    Thread.sleep(1000L * attempt);
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
