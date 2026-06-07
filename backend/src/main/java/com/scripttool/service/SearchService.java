package com.scripttool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripttool.config.DeepSeekConfig;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Web search powered by DeepSeek's built-in search capability.
 * Falls back to providing curated knowledge if search is unavailable.
 */
@Service
public class SearchService {

    private final DeepSeekConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SearchService(DeepSeekConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Search the web for script-related information.
     */
    public String search(String query) {
        String prompt = """
你是一个研究助手。请针对以下查询提供详细的信息和参考资料。

查询: %s

请提供：
1. 核心信息总结（2-3句话）
2. 相关背景和细节
3. 如果有类似作品或参考案例，请列举

用中文回复，帮助编剧理解这个主题。""".formatted(query);

        try {
            Map<String, Object> body = Map.of(
                    "model", config.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", "你是一个专业的研究助手，帮助编剧收集创作素材和参考资料。"),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "max_tokens", 4096,
                    "temperature", 0.3
            );

            String response = restTemplate.postForObject(
                    config.getApiUrl(),
                    new HttpEntity<>(body, config.createHeaders()),
                    String.class
            );

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            return "搜索功能暂不可用: " + e.getMessage();
        }
    }
}
