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
        String prompt = String.format(
            "电影级场景, %s, 地点: %s, 时间: %s, 氛围: %s, 中国风, 高质量, cinematic lighting, highly detailed",
            desc != null ? desc : "戏剧场景",
            location != null ? location : "古代中国",
            time != null ? time : "黄昏",
            mood != null ? mood : "戏剧性"
        );
        return generateWithTokenHub(prompt, "scene");
    }

    public String generateCharacterImage(String name, String description, List<String> traits) {
        String prompt = String.format(
            "角色肖像, %s, %s, 特征: %s, 电影灯光, 专业摄影, 高质量, professional portrait photography",
            name != null ? name : "角色",
            description != null ? description : "历史人物",
            traits != null ? String.join(", ", traits) : "神秘"
        );
        return generateWithTokenHub(prompt, "character");
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
