package com.scripttool.controller;

import com.scripttool.model.dto.ApiResponse;
import com.scripttool.model.entity.ImageVersion;
import com.scripttool.service.ImageService;
import com.scripttool.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final SearchService searchService;
    private final ImageService imageService;

    public AiController(SearchService searchService, ImageService imageService) {
        this.searchService = searchService;
        this.imageService = imageService;
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<?>> search(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "查询不能为空"));
        }
        String result = searchService.search(query);
        return ResponseEntity.ok(ApiResponse.success(Map.of("result", result)));
    }

    @PostMapping("/image/scene")
    public ResponseEntity<ApiResponse<?>> generateSceneImage(@RequestBody Map<String, Object> body) {
        try {
            Long projectId = toLong(body.get("projectId"));
            int sceneIndex = toInt(body.get("sceneIndex"));
            Map<String, Object> result = imageService.generateSceneImage(
                    projectId, sceneIndex,
                    (String) body.get("description"),
                    (String) body.get("location"),
                    (String) body.get("time"),
                    (String) body.get("mood")
            );
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/image/character")
    public ResponseEntity<ApiResponse<?>> generateCharacterImage(@RequestBody Map<String, Object> body) {
        try {
            Long projectId = toLong(body.get("projectId"));
            int charIndex = toInt(body.get("charIndex"));
            @SuppressWarnings("unchecked")
            var traits = (java.util.List<String>) body.getOrDefault("traits", java.util.List.of());
            Map<String, Object> result = imageService.generateCharacterImage(
                    projectId, charIndex,
                    (String) body.get("name"),
                    (String) body.get("description"),
                    traits
            );
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /** Get image version history */
    @GetMapping("/image/versions")
    public ResponseEntity<ApiResponse<?>> getVersions(
            @RequestParam Long projectId,
            @RequestParam String type,
            @RequestParam(defaultValue = "0") int index) {
        try {
            ImageVersion.ImageType imageType = ImageVersion.ImageType.valueOf(type.toUpperCase());
            List<ImageVersion> versions = imageService.getVersions(projectId, imageType, index);
            return ResponseEntity.ok(ApiResponse.success(versions));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "无效类型: " + type));
        }
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        return null;
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) return Integer.parseInt(s);
        return 0;
    }
}
