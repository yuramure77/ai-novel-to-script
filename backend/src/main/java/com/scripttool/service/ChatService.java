package com.scripttool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripttool.config.DeepSeekConfig;
import com.scripttool.model.entity.Conversation;
import com.scripttool.model.entity.Message;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.repository.ConversationRepository;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ChatService {

    private final ConversationRepository convRepo;
    private final ProjectService projectService;
    private final DeepSeekConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ChatService(ConversationRepository convRepo, ProjectService projectService,
                       DeepSeekConfig config, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.convRepo = convRepo;
        this.projectService = projectService;
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatResponse chat(Long projectId, Long userId, String userMessage) {
        Conversation conv = convRepo.findTopByProjectIdOrderByCreatedAtDesc(projectId)
                .orElseGet(() -> convRepo.save(new Conversation(projectId, userId)));

        ScriptVersion script = projectService.getLatestScriptVersion(projectId);
        String scriptContext = script != null ? script.getYamlContent() : "";

        List<Map<String, String>> aiMessages = new ArrayList<>();
        aiMessages.add(Map.of("role", "system", "content", buildSystemPrompt(scriptContext)));
        for (Message m : conv.getMessages()) {
            aiMessages.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }
        aiMessages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = Map.of(
                "model", config.getModel(),
                "messages", aiMessages,
                "max_tokens", config.getMaxTokens(),
                "temperature", 0.7
        );

        String response = restTemplate.postForObject(
                config.getApiUrl(),
                new HttpEntity<>(body, config.createHeaders()),
                String.class
        );

        String reply = extractReply(response);

        // Extract YAML patch if present
        String yamlPatch = extractYamlPatch(reply);

        conv.getMessages().add(new Message(conv, "user", userMessage));
        conv.getMessages().add(new Message(conv, "assistant", reply));
        convRepo.save(conv);

        return new ChatResponse(reply, conv.getId(), yamlPatch, parseActions(reply));
    }

    private String buildSystemPrompt(String scriptContent) {
        return """
你是一个专业的剧本编辑助手。你可以：
1. 分析剧本结构、节奏、角色发展
2. 优化对话和场景描写
3. 提出具体的修改建议

## YAML 修改格式
如果你想建议修改剧本 YAML，请使用以下格式：

```yaml:patch
# 说明修改原因
characters:
  - name: "角色名"
    # ... (修改后的完整角色块)

scenes:
  - id: SCENE_001
    # ... (修改后的完整场景块)
```

如果只是回答问题或给建议，不要包含 yaml:patch 代码块。

## 写作建议
- 具体的修改优于笼统的建议
- 如果用户要看某个角色，直接给出角色信息
- 如果用户要求改某段台词，给出修改前后的对比

当前剧本：
%s""".formatted(scriptContent);
    }

    private String extractReply(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("解析 AI 响应失败", e);
        }
    }

    private String extractYamlPatch(String reply) {
        int start = reply.indexOf("```yaml:patch");
        if (start < 0) start = reply.indexOf("```yaml");
        if (start < 0) return null;
        int contentStart = reply.indexOf('\n', start);
        int end = reply.indexOf("```", contentStart + 1);
        if (contentStart > 0 && end > contentStart) {
            String yaml = reply.substring(contentStart + 1, end).trim();
            return yaml.replace(":patch", "");
        }
        return null;
    }

    private List<Map<String, String>> parseActions(String reply) {
        List<Map<String, String>> actions = new ArrayList<>();
        if (reply.contains("```yaml:patch") || reply.contains("```yaml")) {
            actions.add(Map.of("label", "应用此修改", "action", "apply_yaml", "style", "primary"));
            actions.add(Map.of("label", "忽略", "action", "discard", "style", "default"));
        }
        return actions;
    }

    public List<Message> getHistory(Long projectId) {
        return convRepo.findTopByProjectIdOrderByCreatedAtDesc(projectId)
                .map(Conversation::getMessages)
                .orElse(List.of());
    }

    public record ChatResponse(String reply, Long conversationId, String yamlPatch, List<Map<String, String>> actions) {}
}
