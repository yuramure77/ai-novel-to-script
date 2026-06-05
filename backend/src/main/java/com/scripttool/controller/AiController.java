package com.scripttool.controller;

import com.scripttool.model.dto.ApiResponse;
import com.scripttool.service.ImageService;
import com.scripttool.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<ApiResponse<?>> generateSceneImage(@RequestBody Map<String, String> body) {
        String url = imageService.generateSceneImage(
                body.get("description"),
                body.get("location"),
                body.get("time"),
                body.get("mood")
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }

    @PostMapping("/image/character")
    public ResponseEntity<ApiResponse<?>> generateCharacterImage(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        var traits = (java.util.List<String>) body.getOrDefault("traits", java.util.List.of());
        String url = imageService.generateCharacterImage(
                (String) body.get("name"),
                (String) body.get("description"),
                traits
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }
}
