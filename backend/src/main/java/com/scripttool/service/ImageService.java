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
        String visualDesc = enrichPrompt("场景", base, "", "");
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
        // 1. Detect age + gender from character info
        String ageGroup = detectAge(name, description, traits);
        String gender = detectGender(name, description, traits);

        // 2. Build character info with age + gender anchors
        StringBuilder base = new StringBuilder();
        base.append("角色名：").append(name != null ? name : "未知角色").append("。");
        base.append("性别：").append(gender).append("。");
        base.append("年龄阶段：").append(ageGroup).append("。");
        if (description != null && !description.isBlank()) {
            base.append("角色描述：").append(description).append("。");
        }
        if (traits != null && !traits.isEmpty()) {
            base.append("性格特征：").append(String.join("、", traits)).append("。");
        }

        // 3. DeepSeek enrichment
        String visualDesc = enrichPrompt("角色", base.toString(), ageGroup, gender);

        // 4. Build final prompt with age + gender anchors
        String ageAnchor = ageGroup.equals("老年") ? "，老年人物，银发白发，面部皱纹，老年体态"
                : ageGroup.equals("中年") ? "，中年人物，成熟面容"
                : ageGroup.equals("儿童") ? "，儿童，年幼面容"
                : "，青年人物";
        String genderAnchor = gender.equals("女") ? "，女性人物" : "，男性人物";

        String prompt = visualDesc + "，古风人物写真，中国古代服饰，精致发冠与头饰，"
                + "电影人像摄影，柔和自然侧光，半身肖像构图，浅景深背景虚化，"
                + "精致五官细节，皮肤质感真实" + ageAnchor + genderAnchor + "，古装剧定妆照风格，高品质，8K超清";

        // 5. Generate image
        String url = generateWithTokenHub(prompt);

        // 6. Save version
        versionRepo.save(new ImageVersion(projectId, ImageType.CHARACTER, charIndex, url, prompt));

        return Map.of("url", url, "prompt", prompt);
    }

    /** Detect age group from character info keywords */
    private String detectAge(String name, String description, List<String> traits) {
        StringBuilder combined = new StringBuilder();
        if (name != null) combined.append(name);
        if (description != null) combined.append(description);
        if (traits != null) traits.forEach(combined::append);

        String text = combined.toString();
        if (text.matches(".*(老|年迈|祖母|祖父|奶奶|爷爷|长老|老妇|老夫|白发|苍老|老年|老朽|老太|姥|曾祖|高龄|古稀|花甲|耄耋).*"))
            return "老年";
        if (text.matches(".*(中年|壮年|妇|婶|叔|伯|姨|舅|爹|娘|父|母|师|掌柜|员外|夫人).*"))
            return "中年";
        if (text.matches(".*(童|孩|幼|少|小).*"))
            return "儿童";
        return "青年";
    }

    /** Detect gender from character info keywords */
    private String detectGender(String name, String description, List<String> traits) {
        StringBuilder combined = new StringBuilder();
        if (name != null) combined.append(name);
        if (description != null) combined.append(description);
        if (traits != null) traits.forEach(combined::append);
        String text = combined.toString();

        if (text.matches(".*(公主|小姐|夫人|太太|姐姐|妹妹|姑|姨|母后|王妃|皇后|妃|娘|姬|妾|婢|女侠|郡主|千金|少女|女|她).*"))
            return "女";
        if (text.matches(".*(殿下|王子|王爷|公子|少爷|先生|兄|弟|伯|叔|父皇|帝王|皇帝|皇|郎|爷|男|他|太子|君主|侯|将).*"))
            return "男";
        return "男"; // default male for historical drama
    }

    /**
     * Use DeepSeek to generate a visually-rich Chinese description for AI image generation.
     * Falls back to the original base text if DeepSeek call fails.
     */
    private String enrichPrompt(String type, String baseInfo, String ageGroup, String gender) {
        try {
            String systemPrompt;
            if (type.equals("角色")) {
                String genderRule = (gender == null || gender.isEmpty()) ? ""
                    : "该角色性别为" + gender + "，必须严格按此性别描写外貌特征。";
                String ageRule = (ageGroup == null || ageGroup.isEmpty())
                    ? "" : "严格按年龄阶段描写：" + ageGroup + "人物——"
                      + ("老年".equals(ageGroup) ? "须有银发/白发、面部皱纹、老年体态，切忌年轻化。"
                         : "中年".equals(ageGroup) ? "须有成熟面容、岁月痕迹，切忌年轻化。"
                         : "儿童".equals(ageGroup) ? "须有年幼面容、童真神态。"
                         : "须有青春气息。");
                systemPrompt = "你是一位古装影视剧造型师。请根据角色信息，用高度个性化的中文描述其外貌（80字以内）。"
                  + "必须包含：脸型、眉形眼型、发型头饰、服饰风格、体型姿态、标志性特征。"
                  + genderRule
                  + ageRule
                  + "关键：突出该角色区别于其他人物的独特外貌，避免千篇一律的「剑眉星目」「面如冠玉」。"
                  + "性格特质要转化为视觉元素——如「孤傲」→下颌微扬冷峻神情，「温柔」→眼含笑意体态柔和。"
                  + "参考三岛由纪夫《春雪》中松枝清显「纤细近乎病态」与本多繁邦「方正刚毅」的对比写法。";
            } else {
                systemPrompt = "你是一位古装影视剧美术指导。请根据场景信息，用高度个性化的中文描述环境画面（80字以内）。"
                  + "必须包含：独特的环境氛围、有辨识度的光影色调、建筑或自然景观的鲜明特征。避免套路化描写。";
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", deepSeekConfig.getModel());
            body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", baseInfo
                    + "\n请创作高度个性化的视觉描写，突出该" + type + "区别于其他人物的独特外貌特征：")
            ));
            body.put("max_tokens", 300);
            body.put("temperature", 0.9);

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

    /** Get latest image URL + prompt for all characters and scenes in a project */
    public Map<String, Object> getLatestImages(Long projectId) {
        List<ImageVersion> all = versionRepo.findByProjectIdOrderByCreatedAtDesc(projectId);
        Map<String, Map<String, Object>> chars = new LinkedHashMap<>();
        Map<String, Map<String, Object>> scenes = new LinkedHashMap<>();

        for (ImageVersion v : all) {
            String key = String.valueOf(v.getTargetIndex());
            if (v.getImageType() == ImageType.CHARACTER) {
                chars.putIfAbsent(key, Map.of("url", v.getUrl(), "prompt",
                        v.getPrompt() != null ? v.getPrompt() : ""));
            } else {
                scenes.putIfAbsent(key, Map.of("url", v.getUrl(), "prompt",
                        v.getPrompt() != null ? v.getPrompt() : ""));
            }
        }

        return Map.of("characters", chars, "scenes", scenes);
    }
}
