package com.scripttool.controller;

import com.scripttool.model.dto.ApiResponse;
import com.scripttool.model.entity.Message;
import com.scripttool.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/{projectId}")
    public ResponseEntity<ApiResponse<?>> chat(@PathVariable Long projectId,
                                                @RequestBody Map<String, String> body,
                                                Authentication auth) {
        try {
            Long userId = (Long) auth.getPrincipal();
            String message = body.get("message");
            if (message == null || message.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "消息不能为空"));
            }

            ChatService.ChatResponse reply = chatService.chat(projectId, userId, message);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "reply", reply.reply(),
                    "conversationId", reply.conversationId()
            )));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/{projectId}/history")
    public ResponseEntity<ApiResponse<?>> history(@PathVariable Long projectId) {
        List<Message> messages = chatService.getHistory(projectId);
        List<Map<String, Object>> result = messages.stream()
                .map(m -> Map.<String, Object>of("role", m.getRole(), "content", m.getContent()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
