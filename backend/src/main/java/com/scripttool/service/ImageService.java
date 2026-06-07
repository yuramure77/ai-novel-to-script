package com.scripttool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripttool.config.DeepSeekConfig;
import com.scripttool.model.entity.ImageVersion;
import com.scripttool.model.entity.ImageVersion.ImageType;
import com.scripttool.repository.ImageVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);
    // Circuit breaker: stop trying after N consecutive TokenHub failures
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private int consecutiveFailures = 0;
    private long circuitOpenUntil = 0;

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
        log.info("[生图] 场景图开始 projectId={} idx={}", projectId, sceneIndex);
        // 1. Build rich prompt via DeepSeek
        String base = String.format("场景：%s。地点：%s。时辰：%s。氛围：%s。",
                desc != null ? desc : "戏剧场景",
                location != null ? location : "古代中国",
                time != null ? time : "黄昏",
                mood != null && !mood.isBlank() ? mood : "戏剧性");
        String visualDesc = enrichPrompt("场景", base, "", "", "");
        String prompt = visualDesc + "，电影级构图与光影，广角镜头，丰富的环境细节，"
                + "史诗感，古装大片风格，高质量渲染，8K超清画质";
        log.info("[生图] 场景prompt生成完成, 长度={}", prompt.length());

        // 2. Generate image
        String url = generateWithTokenHub(prompt);

        // 3. Save version
        versionRepo.save(new ImageVersion(projectId, ImageType.SCENE, sceneIndex, url, prompt));
        log.info("[生图] 场景图完成 idx={}", sceneIndex);

        return Map.of("url", url, "prompt", prompt);
    }

    /**
     * Generate character image with DeepSeek-enriched visual description + COS + version save.
     */
    public Map<String, Object> generateCharacterImage(Long projectId, int charIndex,
                                                       String name, String description, List<String> traits) {
        // 1. Detect age + gender + ethnicity from character info
        String ageGroup = detectAge(name, description, traits);
        String gender = detectGender(name, description, traits);
        String ethnicity = detectEthnicity(name, description, traits);

        // 2. Build character info with age + gender + ethnicity anchors
        StringBuilder base = new StringBuilder();
        base.append("角色名：").append(name != null ? name : "未知角色").append("。");
        base.append("性别：").append(gender).append("。");
        base.append("年龄阶段：").append(ageGroup).append("。");
        base.append("人种/地域：").append(ethnicity).append("。");
        if (description != null && !description.isBlank()) {
            base.append("角色描述：").append(description).append("。");
        }
        if (traits != null && !traits.isEmpty()) {
            base.append("性格特征：").append(String.join("、", traits)).append("。");
        }

        // 3. DeepSeek enrichment
        String visualDesc = enrichPrompt("角色", base.toString(), ageGroup, gender, ethnicity);

        // 4. Build final prompt with age + gender + ethnicity anchors
        String ageAnchor;
        switch (ageGroup) {
            case "老年": ageAnchor = "，老年人物，银发或白发，面部皱纹与岁月痕迹，老年体态"; break;
            case "中年": ageAnchor = "，中年人物，成熟面容，阅历感与稳重气质"; break;
            case "少年": ageAnchor = "，少年人物，青涩面容，清瘦身材，青春气息"; break;
            case "儿童": ageAnchor = "，儿童，年幼圆润面容，天真神态"; break;
            default:     ageAnchor = "，青年人物"; break;
        }
        String genderAnchor;
        if (gender.equals("女")) {
            genderAnchor = "，女性人物";
        } else if (gender.equals("男")) {
            genderAnchor = "，男性人物";
        } else {
            genderAnchor = "";
        }

        // Ethnicity anchor: translate to visual cues
        String ethnicityAnchor = buildEthnicityAnchor(ethnicity);

        // Era anchor: if East Asian historical, use 古风; otherwise use character-appropriate setting
        String eraStyle = ethnicity.contains("东亚") || ethnicity.contains("未知")
                ? "，古风人物写真，中国古代服饰，精致发冠与头饰"
                : "，" + ethnicity + "传统服饰，历史剧风格";

        String prompt = visualDesc + eraStyle
                + "，电影人像摄影，柔和自然侧光，半身肖像构图，浅景深背景虚化，"
                + "精致五官细节，皮肤质感真实" + ageAnchor + genderAnchor + ethnicityAnchor
                + "，高品质，8K超清";

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
        // 老年 — senior keywords
        if (text.matches(".*(老|年迈|祖母|祖父|奶奶|爷爷|长老|老妇|老夫|白发|苍老|老年|老朽|老太|姥|曾祖|高龄|古稀|花甲|耄耋|杖朝|知命|耳顺|暮年|垂暮|晚景|风烛|年逾|鲐背|期颐|老者|老叟|老妪|鹤发|衰老|老态|年过).*"))
            return "老年";
        // 中年 — mature adult keywords (checked before 青年 to avoid misclassification)
        if (text.matches(".*(中年|壮年|妇人|婶|叔|伯|姨|舅|爹|娘|父|母|掌柜|员外|夫人|太太|老爷|老爷|师父|师傅|家主|主母|当家|三十|四十|五十|中年男子|中年女子|中年男人|中年女人).*"))
            return "中年";
        // 少年 — teen/pre-teen (checked before general 儿童 to catch 少年 specifically)
        if (text.matches(".*(少年|十六七|十三四|十五六|十二三|十一二|及笄|束发|豆蔻|弱冠|童子|童女|少年郎|翩翩少年|青涩少年).*"))
            return "少年";
        // 儿童 — child
        if (text.matches(".*(儿童|孩童|小孩|幼|稚子|黄口|总角|垂髫|童子|五岁|六岁|七岁|八岁|三岁|四岁).*"))
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

        // Female indicators
        if (text.matches(".*(公主|小姐|夫人|太太|姐姐|妹妹|姑|姨|母后|王妃|皇后|妃|娘|姬|妾|婢|女侠|郡主|千金|少女|女|她|千金|淑女|闺女|娘子|姑娘"
                + "|嫂|姐|奶|婆|姐|姝|媛|嫔|妃子|佳丽|红颜|妇人|主母|小姐|女眷|巾帼|姊妹|姐妹"
                + ").*"))
            return "女";
        // Male indicators
        if (text.matches(".*(殿下|王子|王爷|公子|少爷|先生|兄|弟|伯|叔|父皇|帝王|皇帝|皇|郎|爷|男|他|太子|君主|侯|将"
                + "|士|君|子|生|丈|翁|公|兄|弟|侠客|壮士|老林|猎户|和尚|道士"
                + ").*"))
            return "男";

        // Fallback: check traits for gender-coded words
        if (traits != null) {
            for (String t : traits) {
                if (t.matches(".*(温柔|贤惠|端庄|妩媚|娇|婀娜|婉约|纤柔|秀丽|娟秀|俏).*"))
                    return "女";
                if (t.matches(".*(豪迈|刚毅|魁梧|威猛|英武|粗犷|铁血|沉稳|挺拔|俊朗).*"))
                    return "男";
            }
        }

        return "未知"; // let DeepSeek infer from context
    }

    /** Detect ethnicity/region from character info keywords */
    private String detectEthnicity(String name, String description, List<String> traits) {
        StringBuilder combined = new StringBuilder();
        if (name != null) combined.append(name);
        if (description != null) combined.append(description);
        if (traits != null) traits.forEach(combined::append);
        String text = combined.toString();

        // European / Caucasian
        if (text.matches(".*(欧美|欧洲|西方|英国|法国|德国|意大利|西班牙|俄罗斯|美国|白人|金发|蓝眼|碧眼|绿眼|灰眼|深眼窝|高鼻梁|白皮|北欧|维京|骑士|领主|公爵|子爵|伯爵|男爵|吸血鬼|精灵|矮人).*"))
            return "欧洲";
        // South Asian (Indian subcontinent)
        if (text.matches(".*(印度|巴基斯坦|孟加拉|斯里兰卡|尼泊尔|婆罗门|刹帝利|瑜伽|僧伽罗|加尔各答).*"))
            return "南亚";
        // Middle Eastern
        if (text.matches(".*(中东|阿拉伯|波斯|土耳其|伊朗|伊拉克|埃及|沙特|酋长|苏丹|哈里发|帕夏|奥斯曼|巴格达|大马士革).*"))
            return "中东";
        // Southeast Asian
        if (text.matches(".*(东南亚|泰国|越南|缅甸|柬埔寨|印尼|马来|菲律宾|老挝|暹罗).*"))
            return "东南亚";
        // African
        if (text.matches(".*(非洲|埃及|尼日利亚|肯尼亚|南非|埃塞俄比亚|黑人|卷发|深黑皮).*"))
            return "非洲";
        // Latin / Hispanic
        if (text.matches(".*(拉丁|墨西哥|巴西|阿根廷|西班牙|葡萄牙|南美|混血).*"))
            return "拉丁";
        // East Asian — detected by absence of Western/Middle Eastern/South Asian
        if (text.matches(".*(中国|中华|中原|华夏|汉|唐|宋|明|清|秦|楚国|魏|蜀|吴|晋|隋|元|朝|宫|府|县|州|郡|镇|村|寨|京|都|江南|塞北|西域|关东|岭南|巴蜀|昆仑|武当|少林|峨眉|华山|中原|春秋|战国|三国|水浒|红楼|西游|聊斋).*"))
            return "东亚";
        // Japanese
        if (text.matches(".*(日本|倭|江户|京都|奈良|平安|幕府|武士|忍者|艺伎|大名|浪人|军记|和风|樱花|富士).*"))
            return "东亚·日本";
        // Korean
        if (text.matches(".*(韩国|朝鲜|高丽|百济|新罗|高句丽|两班|士大夫|济州|汉阳).*"))
            return "东亚·朝鲜";

        // Default: context-dependent — most novels in this tool are Chinese historical
        return "东亚"; // default for Chinese novels
    }

    /** Build ethnicity-specific visual anchor for image prompt */
    private String buildEthnicityAnchor(String ethnicity) {
        if (ethnicity == null || ethnicity.equals("东亚")) {
            return "，东亚面容，黑直发，深棕瞳色，肤白或浅黄";
        }
        switch (ethnicity) {
            case "东亚·日本": return "，东亚面容，黑直发或传统日式发型，深棕瞳色，肤白";
            case "东亚·朝鲜": return "，东亚面容，黑直发，深棕瞳色，朝鲜传统发髻";
            case "欧洲":       return "，欧洲面容，深眼窝高鼻梁，发色瞳色多样，肤白或浅粉";
            case "南亚":       return "，南亚面容，大眼深黑发，深棕肤色，面部轮廓立体";
            case "中东":       return "，中东面容，橄榄色皮肤，黑浓眉深眼窝，黑或褐发";
            case "东南亚":     return "，东南亚面容，浅棕肤色，黑发黑眼";
            case "非洲":       return "，非洲面容，深黑肤色，卷发或编发，面部轮廓鲜明";
            case "拉丁":       return "，拉丁面容，棕肤色，黑或深褐发，混血面部特征";
            default:           return "";
        }
    }

    /**
     * Use DeepSeek to generate a visually-rich Chinese description for AI image generation.
     * Falls back to the original base text if DeepSeek call fails.
     */
    private String enrichPrompt(String type, String baseInfo, String ageGroup, String gender, String ethnicity) {
        try {
            String systemPrompt;
            if (type.equals("角色")) {
                // Gender rule
                String genderRule;
                if (gender == null || gender.isEmpty() || gender.equals("未知")) {
                    genderRule = "若角色信息未明确性别，请根据角色名和描述合理推断，并据此描写外貌。";
                } else {
                    genderRule = "该角色为" + gender + "性，必须严格按此性别描写外貌特征。";
                }
                // Age rule
                String ageRule;
                if (ageGroup == null || ageGroup.isEmpty()) {
                    ageRule = "根据角色信息合理推断年龄阶段并据此描写。";
                } else {
                    ageRule = "严格按年龄阶段描写：" + ageGroup + "人物——"
                      + ("老年".equals(ageGroup) ? "须有银发/白发、面部皱纹、老年体态，切忌年轻化。"
                         : "中年".equals(ageGroup) ? "须有成熟面容、岁月痕迹与稳重气质，切忌年轻化。"
                         : "少年".equals(ageGroup) ? "须有青涩面容、清瘦身量、青春朝气。"
                         : "儿童".equals(ageGroup) ? "须有年幼圆润面容、天真神态、童稚气质。"
                         : "须有青春气息、匀称体态。");
                }
                // Ethnicity rule
                String ethnicityRule;
                if (ethnicity == null || ethnicity.isEmpty() || ethnicity.equals("东亚")) {
                    ethnicityRule = "人物为东亚人种（中国/日本/朝鲜等）：黑直发、深棕瞳色、面部轮廓较平坦、肤色偏白或浅黄。"
                        + "发型头饰须符合中国古代规制——男子束发戴冠或幞头，女子梳髻插钗环或步摇。";
                } else {
                    ethnicityRule = "人物为" + ethnicity + "人种，须严格按照该人种的面部特征、发型发色、肤色瞳色来描写。"
                        + "服饰须匹配" + ethnicity + "传统风格，切忌穿越混搭。";
                }

                systemPrompt = "你是一位影视剧造型师，精通各人种和历史时期的妆造设计。"
                  + "请根据角色信息，用高度个性化的中文描述其外貌（120字以内）。\n"
                  + "必须覆盖以下维度，每个角色至少2项与他人截然不同：\n"
                  + "1. 脸型（瓜子脸/鹅蛋脸/方脸/圆脸/长脸/菱形脸等，须与性格气质匹配）\n"
                  + "2. 眉形与眼型（含眼神气质——锐利/温柔/忧郁/灵动/呆滞/深邃/迷离等）\n"
                  + "3. 发型与头饰（须匹配人种发质+身份地位+时代背景）\n"
                  + "4. 服饰风格与材质（丝绸/棉麻/华服/布衣/皮革/铠甲等，匹配身份性格）\n"
                  + "5. 体型与姿态（魁梧/瘦削/匀称/娇小/挺拔/佝偻/丰腴/清癯等）\n"
                  + "6. 肤色与肤质（白/黄/棕/黑/古铜/红润/蜡黄/粗糙/细腻/雀斑等，须匹配人种）\n"
                  + "7. 标志性特征（痣/伤疤/胎记/独特配饰/习惯手势/体态特征等，每角色至少一处）\n"
                  + "8. 气质与气场（威严/亲和/冷艳/儒雅/阴鸷/阳光/邪魅/端庄/灵动等）\n"
                  + "9. 妆容特点（古代女性须有妆面描写——花钿/面靥/胭脂/唇脂/眉黛等）\n"
                  + genderRule + "\n"
                  + ageRule + "\n"
                  + ethnicityRule + "\n"
                  + "关键：突出该角色区别于其他人物的独特外貌，避免千篇一律的「剑眉星目」「面如冠玉」。\n"
                  + "性格→视觉转化对照：\n"
                  + "  「孤傲」→ 下颌微扬、冷峻疏离的眼神、身形笔挺\n"
                  + "  「温柔」→ 眼含笑意、体态柔和、眉形舒展\n"
                  + "  「阴郁」→ 眼底阴影、苍白肤色、眉间微蹙\n"
                  + "  「豪爽」→ 浓眉方脸、魁梧身形、肤色偏深\n"
                  + "  「狡黠」→ 嘴角微翘、眼神灵动、挑眉\n"
                  + "  「忧郁」→ 眉间微蹙、低头垂目、气质清冷\n"
                  + "  「坚韧」→ 神情坚毅、站姿挺拔、下颌线条硬朗\n"
                  + "  「残暴」→ 横眉怒目、面部疤痕、粗壮身形\n"
                  + "  「睿智」→ 目光深邃、眉宇间有思虑纹、清癯体态\n"
                  + "参考三岛由纪夫《春雪》中松枝清显「纤细近乎病态」与本多繁邦「方正刚毅」的对比写法。"
                  + "不同人种角色须有明显面部结构差异——东亚面部平坦、鼻梁适中；欧洲深眼窝高鼻梁窄脸。";
            } else {
                systemPrompt = "你是一位影视剧美术指导，精通各时代和地域的视觉设计。"
                  + "请根据场景信息，用高度个性化的中文描述环境画面（100字以内）。\n"
                  + "必须覆盖以下维度：\n"
                  + "1. 核心视觉焦点（画面中最抓眼的主体——建筑/自然景观/人物剪影等）\n"
                  + "2. 光影色调（金灿暮光/冷蓝月夜/昏黄油灯/灰蒙雨雾/暖橙烛光等，须有辨识度）\n"
                  + "3. 空间层次（前景/中景/远景各有什么，营造纵深感）\n"
                  + "4. 环境细节（材质质感、天气效果、植被类型、建筑风格等）\n"
                  + "5. 氛围基调（静谧/紧张/浪漫/荒凉/肃杀/温馨/神秘等，须与场景情绪一致）\n"
                  + "避免套路化描写。每个场景须有一个独特的记忆点。";
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", deepSeekConfig.getModel());
            body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", baseInfo
                    + "\n请按上述维度创作高度个性化的视觉描写，"
                    + "突出该" + type + "区别于其他" + type + "的独特视觉特征：")
            ));
            body.put("max_tokens", 500);
            body.put("temperature", 0.9);

            HttpHeaders headers = deepSeekConfig.createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String response = restTemplate.postForObject(deepSeekConfig.getApiUrl(), entity, String.class);

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            return content != null && !content.isBlank() ? content.trim() : baseInfo;
        } catch (Exception e) {
            log.warn("[生图] DeepSeek提示词增强失败, type={}, 使用原始描述. 错误: {}", type, e.getMessage());
            return baseInfo; // fallback to original
        }
    }

    /**
     * Call TokenHub hy-image-lite API, then upload to COS for permanent storage.
     */
    private String generateWithTokenHub(String prompt) {
        // Circuit breaker: if TokenHub consistently fails, stop trying to save DeepSeek credits
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            if (System.currentTimeMillis() < circuitOpenUntil) {
                throw new RuntimeException("TokenHub已熔断: 连续失败" + consecutiveFailures
                    + "次, " + (circuitOpenUntil - System.currentTimeMillis())/1000 + "秒后重试");
            }
            consecutiveFailures = 0; // reset after cooldown
        }
        log.info("[生图] TokenHub请求开始, model={}, prompt前50字={}", model,
            prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
            "model", model,
            "prompt", prompt,
            "rsp_img_type", "url"
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(apiUrl, entity, String.class);
            consecutiveFailures = 0; // success → reset circuit breaker
            log.info("[生图] TokenHub HTTP状态={}, 响应长度={}",
                response.getStatusCode().value(),
                response.getBody() != null ? response.getBody().length() : 0);
        } catch (Exception e) {
            consecutiveFailures++;
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                circuitOpenUntil = System.currentTimeMillis() + 60_000; // 1 min cooldown
                log.error("[生图] 熔断触发! 连续失败{}次, 暂停60秒", consecutiveFailures);
            }
            log.error("[生图] TokenHub HTTP请求失败({}/{}): {}",
                consecutiveFailures, MAX_CONSECUTIVE_FAILURES, e.getMessage());
            throw new RuntimeException("TokenHub API请求失败: " + e.getMessage(), e);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("[生图] TokenHub响应JSON解析失败: {}", response.getBody(), e);
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
            // Check for JobNumExceed — retry after delay
            JsonNode errNode = root.has("error") ? root.get("error") : null;
            String errCode = errNode != null && errNode.has("code") ? errNode.get("code").asText() : "";
            String errMsg = errNode != null && errNode.has("message") ? errNode.get("message").asText()
                : "无URL返回";

            if ("RequestLimitExceeded.JobNumExceed".equals(errCode)) {
                log.info("[生图] TokenHub并发限制, 等待3秒重试...");
                try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                return generateWithTokenHub(prompt); // retry once
            }

            log.error("[生图] TokenHub未返回图片URL. code={} message={}", errCode, errMsg);
            throw new RuntimeException("TokenHub生图失败: " + errMsg);
        }

        log.info("[生图] TokenHub返回URL: {}", imageUrl.length() > 80 ? imageUrl.substring(0, 80) + "..." : imageUrl);

        // Upload to COS for permanent URL
        if (!cosBucket.isEmpty() && cosService != null) {
            try {
                log.info("[生图] 上传COS...");
                byte[] imageBytes = cosService.downloadImage(imageUrl);
                String cosUrl = cosService.uploadImage(imageBytes, "image");
                log.info("[生图] COS上传成功: {}", cosUrl.length() > 80 ? cosUrl.substring(0, 80) + "..." : cosUrl);
                return cosUrl;
            } catch (Exception e) {
                log.warn("[生图] COS上传失败, 使用TokenHub原始URL: {}", e.getMessage());
            }
        } else {
            log.debug("[生图] COS未配置(bucket={}, cosService={}), 使用原始URL", cosBucket, cosService);
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
