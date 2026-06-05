package com.scripttool.controller;

import com.scripttool.model.dto.ApiResponse;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.repository.ScriptVersionRepository;
import com.scripttool.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ScriptVersionRepository versionRepo;
    private final ExportService exportService;

    public ExportController(ScriptVersionRepository versionRepo, ExportService exportService) {
        this.versionRepo = versionRepo;
        this.exportService = exportService;
    }

    @GetMapping("/{versionId}/markdown")
    public void exportMarkdown(@PathVariable Long versionId, HttpServletResponse response) throws IOException {
        ScriptVersion v = versionRepo.findById(versionId).orElse(null);
        if (v == null) { response.setStatus(404); return; }
        String md = exportService.yamlToMarkdown(v.getYamlContent());
        writeFile(response, md, "script_v" + v.getVersionNumber() + ".md", "text/markdown");
    }

    @GetMapping("/{versionId}/fountain")
    public void exportFountain(@PathVariable Long versionId, HttpServletResponse response) throws IOException {
        ScriptVersion v = versionRepo.findById(versionId).orElse(null);
        if (v == null) { response.setStatus(404); return; }
        String fountain = exportService.yamlToFountain(v.getYamlContent());
        writeFile(response, fountain, "script_v" + v.getVersionNumber() + ".fountain", "text/plain");
    }

    @GetMapping("/{versionId}/preview")
    public ResponseEntity<ApiResponse<?>> preview(@PathVariable Long versionId) {
        ScriptVersion v = versionRepo.findById(versionId).orElse(null);
        if (v == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "markdown", exportService.yamlToMarkdown(v.getYamlContent()),
                "fountain", exportService.yamlToFountain(v.getYamlContent())
        )));
    }

    private void writeFile(HttpServletResponse response, String content, String filename, String mime) throws IOException {
        response.setContentType(mime);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        response.getWriter().write(content);
    }
}
