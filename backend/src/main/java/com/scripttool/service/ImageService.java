package com.scripttool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripttool.config.DeepSeekConfig;
import com.scripttool.model.entity.ImageVersion;
import com.scripttool.model.entity.ImageVersion.ImageType;
import com.scripttool.repository.ImageVersionRepository;
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
    private final DeepSeekConfig deepSeekConfig;

    @Autowired(required = false)
    private CosService cosService;

    @Autowired
    private ImageVersionRepository versionRepo;

    public ImageService(RestTemplate restTemplate, ObjectMapper objectMapper,
                        DeepSeekConfig deepSeekConfig) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.deepSeekConfig = deepSeekConfig;
    }

    /**
     * Generate scene image with DeepSeek-enriched prompt + COS upload + version save.
     */
    public Map<String, Object> generateSceneImage(Long projectId, int sceneIndex,
                                                   String desc, String location, String time, String mood) {
        // 1. Build rich prompt via DeepSeek
        String base = String.format("场景：%s。地点：%s。时辰：%s。氛围：%s。",
                desc != null ? desc : "戏剧场景",
                location != null ? location : "古代中国",
                time != null ? time : "黄昏",
                mood != null && !mood.isBlank() ? mood : "戏剧性");
        String visualDesc = enrichPrompt("场景", base);
        String prompt = visualDesc + "，电影级构图与光影，广角镜头，丰富的环境细节，"
                + "史诗感，古装大片风格，高质量渲染，8K超清画质";

        // 2. Generate image
        String url = generateWithTokenHub(prompt);

        // 3. Save version
        versionRepo.save(new ImageVersion(projectId, ImageType.SCENE, sceneIndex, url, prompt));

        return Map.of("url", url, "prompt", prompt);
    }

    /**
     * Generate character image with DeepSeek-enriched visual description + COS + version save.
     */
    public Map<String, Object> generateCharacterImage(Long projectId, int charIndex,
                                                       String name, String description, List<String> traits) {
        // 1. Build deep character visual description via DeepSeek
        StringBuilder base = new StringBuilder();
        base.append("角色名：").append(name != null ? name : "未知角色").append("。");
        if (description != null && !description.isBlank()) {
            base.append("角色描述：").append(description).append("。");
        }
        if (traits != null && !traits.isEmpty()) {
            base.append("性格特征：").append(String.join("、", traits)).append("。");
        }

        String visualDesc = enrichPrompt("角色", base.toString());
        String prompt = visualDesc + "，古风人物写真，中国古代服饰，精致发冠与头饰，"
                + "电影人像摄影，柔和自然侧光，半身肖像构图，浅景深背景虚化，"
                + "精致五官细节，皮肤质感真实，古装剧定妆照风格，高品质，8K超清";

        // 2. Generate image
        String url = generateWithTokenHub(prompt);

        // 3. Save version
        versionRepo.save(new ImageVersion(projectId, ImageType.CHARACTER, charIndex, url, prompt));

        return Map.of("url", url, "prompt", prompt);
    }

    /**
     * Use DeepSeek to generate a visually-rich Chinese description for AI image generation.
     * Falls back to the original base text if DeepSeek call fails.
     */
    private String enrichPrompt(String type, String baseInfo) {
        try {
            String systemPrompt = type.equals("角色")
                ? "你是一位古装影视剧造型师和美术指导。请根据角色信息，创作一段适合AI图像生成的外貌描写。"
                  + "必须包含：面容特征、发型头饰、服饰风格、气质氛围。用流畅中文描述，80字以内，不要分段编号。"
                  + "参考经典文学作品的描写手法，如《红楼梦》的人物出场描写。"
                : "你是一位古装影视剧美术指导。请根据场景信息，创作一段适合AI图像生成的环境描写。"
                  + "必须包含：环境氛围、光影色调、建筑或自然景观细节。用流畅中文描述，80字以内，不要分段编号。";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", deepSeekConfig.getModel());
            body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", baseInfo + "\n请输出视觉描写：")
            ));
            body.put("max_tokens", 300);
            body.put("temperature", 0.8);

            HttpHeaders headers = deepSeekConfig.createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String response = restTemplate.postForObject(deepSeekConfig.getApiUrl(), entity, String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            return content != null && !content.isBlank() ? content.trim() : baseInfo;
        } catch (Exception e) {
            System.err.println("[ImageService] DeepSeek enrichment failed: " + e.getMessage());
            return baseInfo; // fallback to original
        }
    }

    /**
     * Call TokenHub hy-image-lite API, then upload to COS for permanent storage.
     */
    private String generateWithTokenHub(String prompt) {
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
        JsonNode root;
        try {
            root = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse TokenHub response", e);
        }

        String imageUrl = null;
        JsonNode data = root.get("data");
        if (data != null && data.isArray() && data.size() > 0) {
            imageUrl = data.get(0).get("url").asText();
        } else if (root.get("url") != null) {
            imageUrl = root.get("url").asText();
        }

        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new RuntimeException("TokenHub returned no image URL: " + response.getBody());
        }

        // Upload to COS for permanent URL
        if (!cosBucket.isEmpty() && cosService != null) {
            try {
                byte[] imageBytes = cosService.downloadImage(imageUrl);
                return cosService.uploadImage(imageBytes, "image");
            } catch (Exception e) {
                System.err.println("[ImageService] COS upload failed: " + e.getMessage());
            }
        }

        return imageUrl;
    }

    /** List image versions for a specific character/scene */
    public List<ImageVersion> getVersions(Long projectId, ImageType type, Integer index) {
        return versionRepo.findByProjectIdAndImageTypeAndTargetIndexOrderByCreatedAtDesc(
                projectId, type, index);
    }

    /** Get a specific version by ID */
    public ImageVersion getVersion(Long versionId) {
        return versionRepo.findById(versionId)
                .orElseThrow(() -> new RuntimeException("版本不存在"));
    }
}
