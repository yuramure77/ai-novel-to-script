package com.scripttool.controller;

import com.scripttool.model.dto.ApiResponse;
import com.scripttool.model.dto.ScriptVersionResponse;
import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.repository.ScriptVersionRepository;
import com.scripttool.service.ProjectService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ProjectService projectService;
    private final ScriptVersionRepository versionRepository;

    public ScriptController(ProjectService projectService,
                            ScriptVersionRepository versionRepository) {
        this.projectService = projectService;
        this.versionRepository = versionRepository;
    }

    @GetMapping("/project/{projectId}/versions")
    public ResponseEntity<ApiResponse<?>> listVersions(@PathVariable Long projectId,
                                                        Authentication auth) {
        try {
            Project project = projectService.getProject(projectId);
            if (!project.getUserId().equals((Long) auth.getPrincipal())) {
                return ResponseEntity.status(403).body(ApiResponse.error(403, "无权访问"));
            }

            List<ScriptVersion> versions = projectService.listScriptVersions(projectId);
            List<ScriptVersionResponse> response = versions.stream()
                    .map(ScriptVersionResponse::from)
                    .toList();
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/project/{projectId}/latest")
    public ResponseEntity<ApiResponse<?>> getLatest(@PathVariable Long projectId,
                                                     Authentication auth) {
        try {
            Project project = projectService.getProject(projectId);
            if (!project.getUserId().equals((Long) auth.getPrincipal())) {
                return ResponseEntity.status(403).body(ApiResponse.error(403, "无权访问"));
            }

            ScriptVersion version = projectService.getLatestScriptVersion(projectId);
            if (version == null) {
                return ResponseEntity.ok(ApiResponse.success(null));
            }
            return ResponseEntity.ok(ApiResponse.success(ScriptVersionResponse.from(version)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/{versionId}/yaml")
    public void downloadYaml(@PathVariable Long versionId, HttpServletResponse response) throws IOException {
        ScriptVersion version = versionRepository.findById(versionId).orElse(null);
        if (version == null) {
            response.setStatus(404);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"版本不存在\"}");
            return;
        }

        response.setContentType("application/x-yaml");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=script_v" + version.getVersionNumber() + ".yaml");
        response.getWriter().write(version.getYamlContent());
    }
}
