package com.scripttool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripttool.config.DeepSeekConfig;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ScriptGenService {

    private final DeepSeekConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
你是一个专业的剧本改编助手。你需要将小说章节转换为结构化的剧本格式。

转换规则：
1. 角色的 name 使用原文中的角色名
2. role 取值为: protagonist(主角), antagonist(反派), supporting(配角), minor(次要)
3. scene type 取值为: INT(室内), EXT(室外), INT/EXT(内外交替)
4. beat type 取值为: action(动作/场景描写), dialogue(对白), monologue(独白/内心), narration(旁白), transition(转场)
5. character 字段仅在 beat type 为 dialogue 或 monologue 时需要填写，action/narration/transition 时为 null
6. direction 是表演指导/舞台说明，用中文写
7. emotion 是该 beat 的角色情绪
8. 保留原文的关键对白和重要情节，不要遗漏重要对话
9. 根据上下文推测场景地点和时间，无法判断时标注"未知"
10. 务必返回有效的 JSON，不要包含 markdown 代码块标记

请严格按以下 JSON Schema 返回（不要包含任何其他文字）：
{
  "characters": [
    {
      "name": "角色名",
      "role": "protagonist|antagonist|supporting|minor",
      "description": "外貌与性格描述",
      "traits": ["特长", "性格"]
    }
  ],
  "scenes": [
    {
      "chapter": 1,
      "scene_number": 1,
      "type": "INT|EXT|INT/EXT",
      "location": "地点描述",
      "time": "时间描述",
      "description": "场景氛围与环境描写",
      "characters": ["出场角色名"],
      "beats": [
        {
          "type": "action|dialogue|monologue|narration|transition",
          "character": "角色名 或 null",
          "line": "对白内容 或 null",
          "direction": "表演指导/动作说明",
          "emotion": "情绪"
        }
      ]
    }
  ]
}""";

    public ScriptGenService(DeepSeekConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a complete script for all chapters
     */
    public ScriptResult generateScript(String fullText, List<ChapterSplitService.ChapterResult> chapters) {
        // Step 1: Extract all characters from full text
        List<Map<String, Object>> allCharacters = extractCharacters(fullText, chapters.get(0).number());

        // Step 2: For each chapter, generate scenes
        List<Map<String, Object>> allScenes = new ArrayList<>();
        for (ChapterSplitService.ChapterResult chapter : chapters) {
            List<Map<String, Object>> chapterScenes = generateScenes(
                    chapter.content(), chapter.number(), allCharacters);
            allScenes.addAll(chapterScenes);
        }

        return new ScriptResult(allCharacters, allScenes);
    }

    /**
     * Extract characters from the first chapter (or full text)
     */
    private List<Map<String, Object>> extractCharacters(String text, int chapterNum) {
        String userPrompt = String.format(
                "请提取以下小说文本中的全部角色信息（第%d章）：\n\n%s", chapterNum, text);

        String response = callDeepSeek(userPrompt);
        Map<String, Object> result = parseJsonResponse(response);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> characters = (List<Map<String, Object>>) result.getOrDefault("characters", List.of());
        return characters;
    }

    /**
     * Generate scenes for a single chapter
     */
    private List<Map<String, Object>> generateScenes(String chapterText, int chapterNum,
                                                      List<Map<String, Object>> knownCharacters) {
        String charactersHint = buildCharactersHint(knownCharacters);
        String userPrompt = String.format(
                "请将以下小说章节转换为剧本场景（第%d章）\n\n已知角色：%s\n\n章节原文：%s",
                chapterNum, charactersHint, chapterText);

        String response = callDeepSeek(userPrompt);
        Map<String, Object> result = parseJsonResponse(response);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenes = (List<Map<String, Object>>) result.getOrDefault("scenes", List.of());

        // Ensure chapter number is set
        for (Map<String, Object> scene : scenes) {
            scene.putIfAbsent("chapter", chapterNum);
        }

        return scenes;
    }

    private String buildCharactersHint(List<Map<String, Object>> characters) {
        if (characters.isEmpty()) return "暂无";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> c : characters) {
            sb.append(c.get("name")).append("(").append(c.get("role")).append("), ");
        }
        return sb.substring(0, Math.max(0, sb.length() - 2));
    }

    /**
     * Call DeepSeek API
     */
    private String callDeepSeek(String userPrompt) {
        Map<String, Object> requestBody = Map.of(
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
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String response = restTemplate.postForObject(config.getApiUrl(), entity, String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse DeepSeek API response", e);
        }
    }

    /**
     * Parse JSON from DeepSeek response string
     */
    private Map<String, Object> parseJsonResponse(String jsonStr) {
        try {
            // Clean potential markdown code blocks
            String cleaned = jsonStr.trim();
            if (cleaned.startsWith("```")) {
                int startIdx = cleaned.indexOf('\n');
                int endIdx = cleaned.lastIndexOf("```");
                if (startIdx > 0 && endIdx > startIdx) {
                    cleaned = cleaned.substring(startIdx + 1, endIdx).trim();
                }
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(cleaned, Map.class);
            return result;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse AI response as JSON: " + jsonStr.substring(0, Math.min(200, jsonStr.length())), e);
        }
    }

    public record ScriptResult(List<Map<String, Object>> characters, List<Map<String, Object>> scenes) {}
}
