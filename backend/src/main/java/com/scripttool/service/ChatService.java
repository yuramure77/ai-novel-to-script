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
        // Load or create conversation
        Conversation conv = convRepo.findTopByProjectIdOrderByCreatedAtDesc(projectId)
                .orElseGet(() -> convRepo.save(new Conversation(projectId, userId)));

        // Get current script for context
        ScriptVersion script = projectService.getLatestScriptVersion(projectId);
        String scriptContext = script != null ? script.getYamlContent() : "";

        // Build messages for AI
        List<Map<String, String>> aiMessages = new ArrayList<>();
        aiMessages.add(Map.of("role", "system", "content", buildSystemPrompt(scriptContext)));
        for (Message m : conv.getMessages()) {
            aiMessages.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }
        aiMessages.add(Map.of("role", "user", "content", userMessage));

        // Call DeepSeek
        Map<String, Object> requestBody = Map.of(
                "model", config.getModel(),
                "messages", aiMessages,
                "max_tokens", config.getMaxTokens(),
                "temperature", 0.7
        );

        String response = restTemplate.postForObject(
                config.getApiUrl(),
                new HttpEntity<>(requestBody, config.createHeaders()),
                String.class
        );

        String reply = extractReply(response);

        // Save messages
        conv.getMessages().add(new Message(conv, "user", userMessage));
        conv.getMessages().add(new Message(conv, "assistant", reply));
        convRepo.save(conv);

        return new ChatResponse(reply, conv.getId());
    }

    private String buildSystemPrompt(String scriptContext) {
        return """
你是一个专业的剧本编辑助手。你可以帮助作者：
1. 分析剧本结构和节奏
2. 优化角色对话，使其更自然生动
3. 提出场景调整建议
4. 修改和润色具体段落
5. 回答关于剧本改编的问题

当前剧本内容：
%s

请用中文回复，给出具体可操作的修改建议。如果用户要求修改剧本，请给出具体的修改后文本。""".formatted(scriptContext);
    }

    private String extractReply(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("解析 AI 响应失败", e);
        }
    }

    public List<Message> getHistory(Long projectId) {
        return convRepo.findTopByProjectIdOrderByCreatedAtDesc(projectId)
                .map(Conversation::getMessages)
                .orElse(List.of());
    }

    public record ChatResponse(String reply, Long conversationId) {}
}
