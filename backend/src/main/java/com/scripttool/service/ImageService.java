package com.scripttool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ImageService {

    @Value("${tokenhub.api-key}")
    private String apiKey;

    @Value("${tokenhub.api-url:https://tokenhub.tencentmaas.com/v1/api/image/lite}")
    private String apiUrl;

    @Value("${tokenhub.model:hy-image-lite}")
    private String model;

    @Value("${tencent.cos.bucket:}")
    private String cosBucket;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private CosService cosService;

    public ImageService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String generateSceneImage(String desc, String location, String time, String mood) {
        // Build rich cinematic scene prompt
        StringBuilder sb = new StringBuilder();
        sb.append("电影级场景，");

        if (desc != null && !desc.isBlank()) {
            sb.append(desc).append("，");
        }

        sb.append("地点：").append(location != null ? location : "古代中国").append("，");
        sb.append("时辰：").append(time != null ? time : "黄昏").append("，");

        if (mood != null && !mood.isBlank()) {
            sb.append("氛围：").append(mood).append("，");
        }

        sb.append("中国古代美学，电影构图与光影，广角镜头，");
        sb.append("丰富的环境细节，史诗感，古装大片风格，高质量渲染，8K超清画质");

        return generateWithTokenHub(sb.toString(), "scene");
    }

    public String generateCharacterImage(String name, String description, List<String> traits) {
        // Build high-quality character portrait prompt
        StringBuilder sb = new StringBuilder();
        sb.append("古风人物写真，");

        // Character identity
        if (name != null && !name.isBlank()) {
            sb.append("角色「").append(name).append("」");
        }

        // Visual description from AI analysis
        if (description != null && !description.isBlank()) {
            sb.append("，").append(description);
        } else {
            sb.append("，中国古代人物");
        }

        // Personality traits → visual atmosphere
        if (traits != null && !traits.isEmpty()) {
            sb.append("，性格气质：").append(String.join("、", traits));
        }

        // Artistic direction — Chinese historical drama portrait style
        sb.append("，中国古代服饰，精致发冠与头饰，电影人像摄影，");
        sb.append("柔和自然侧光，半身肖像构图，浅景深背景虚化，");
        sb.append("精致五官细节，皮肤质感真实，古装剧定妆照风格，");
        sb.append("高品质，8K超清");

        return generateWithTokenHub(sb.toString(), "character");
    }

    /**
     * Call TokenHub hy-image-lite API, then upload to COS for permanent storage.
     * Falls back gracefully if COS is not configured.
     */
    private String generateWithTokenHub(String prompt, String prefix) {
        try {
            // 1. Call TokenHub hy-image-lite
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "rsp_img_type", "url"
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            // Parse: {"data": [{"url": "..."}]}
            String imageUrl = null;
            JsonNode data = root.get("data");
            if (data != null && data.isArray() && data.size() > 0) {
                imageUrl = data.get(0).get("url").asText();
            } else if (root.get("url") != null) {
                imageUrl = root.get("url").asText();
            }

            if (imageUrl == null || imageUrl.isEmpty()) {
                throw new RuntimeException("TokenHub returned no image URL");
            }

            // 2. Upload to COS for permanent URL (if configured)
            if (!cosBucket.isEmpty() && cosService != null) {
                try {
                    byte[] imageBytes = cosService.downloadImage(imageUrl);
                    return cosService.uploadImage(imageBytes, prefix);
                } catch (Exception e) {
                    System.err.println("[ImageService] COS upload failed: " + e.getMessage());
                    return imageUrl; // fallback to TokenHub temp URL
                }
            }

            return imageUrl;
        } catch (Exception e) {
            System.err.println("[ImageService] TokenHub failed: " + e.getMessage());
            return fallback(prompt);
        }
    }

    private String fallback(String prompt) {
        return "https://image.pollinations.ai/prompt/" +
                prompt.replace(" ", "%20").replace(",", "%2C") +
                "?width=768&height=768&nologo=true";
    }
}
