package com.scripttool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class ImageService {

    private static final String API_URL = "https://tokenhub.tencentmaas.com/v1/api/image/lite";
    private static final String API_KEY = "sk-ag2nGh9sshMDIsUcgVlMT1TQpCpDitjgQMe2v2QoIkDmQRv5";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ImageService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String generateSceneImage(String desc, String location, String time, String mood) {
        String prompt = String.format(
            "电影级场景, %s, 地点: %s, 时间: %s, 氛围: %s, 中国风, 高质量",
            desc != null ? desc : "戏剧场景",
            location != null ? location : "古代中国",
            time != null ? time : "黄昏",
            mood != null ? mood : "戏剧性"
        );
        return genImage(prompt, "1024x576");
    }

    public String generateCharacterImage(String name, String description, List<String> traits) {
        String prompt = String.format(
            "角色肖像, %s, %s, 特征: %s, 电影灯光, 专业摄影",
            name != null ? name : "角色",
            description != null ? description : "历史人物",
            traits != null ? String.join(", ", traits) : "神秘"
        );
        return genImage(prompt, "768x768");
    }

    private String genImage(String prompt, String size) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(API_KEY);

            Map<String, Object> body = Map.of(
                "model", "hy-image-lite",
                "prompt", prompt,
                "rsp_img_type", "url"
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            // Parse response: {"data": [{"url": "..."}]}
            JsonNode data = root.get("data");
            if (data != null && data.isArray() && data.size() > 0) {
                return data.get(0).get("url").asText();
            }
            return root.get("url") != null ? root.get("url").asText() : "";
        } catch (Exception e) {
            System.err.println("[ImageService] Failed: " + e.getMessage());
            // Fallback to Pollinations.ai
            String fallback = "https://image.pollinations.ai/prompt/" +
                prompt.replace(" ", "%20").replace(",", "%2C") +
                "?width=768&height=768&nologo=true";
            return fallback;
        }
    }
}
