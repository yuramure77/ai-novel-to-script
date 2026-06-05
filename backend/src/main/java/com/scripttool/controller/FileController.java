package com.scripttool.controller;

import com.scripttool.model.dto.ApiResponse;
import com.scripttool.service.FileParserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileParserService parserService;

    public FileController(FileParserService parserService) {
        this.parserService = parserService;
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<?>> upload(@RequestParam("file") MultipartFile file,
                                                  Authentication auth) {
        try {
            String text = parserService.parse(file);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "text", text,
                    "filename", file.getOriginalFilename(),
                    "wordCount", text.length()
            )));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "文件解析失败: " + e.getMessage()));
        }
    }
}
