package com.scripttool.controller;

import com.scripttool.model.dto.ApiResponse;
import com.scripttool.model.dto.ScriptVersionResponse;
import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.repository.ScriptVersionRepository;
import com.scripttool.service.CollaborationService;
import com.scripttool.service.ProjectService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ProjectService projectService;
    private final ScriptVersionRepository versionRepository;
    private final CollaborationService collabService;

    public ScriptController(ProjectService projectService,
                            ScriptVersionRepository versionRepository,
                            CollaborationService collabService) {
        this.projectService = projectService;
        this.versionRepository = versionRepository;
        this.collabService = collabService;
    }

    /** Get script versions — any collaborator can view */
    @GetMapping("/project/{projectId}/versions")
    public ResponseEntity<ApiResponse<?>> listVersions(@PathVariable Long projectId,
                                                        Authentication auth) {
        try {
            Long userId = (Long) auth.getPrincipal();
            Project project = projectService.getProject(projectId);
            if (!collabService.canView(projectId, userId)) {
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

    /** Get latest script — any collaborator can view */
    @GetMapping("/project/{projectId}/latest")
    public ResponseEntity<ApiResponse<?>> getLatest(@PathVariable Long projectId,
                                                     Authentication auth) {
        try {
            Long userId = (Long) auth.getPrincipal();
            Project project = projectService.getProject(projectId);
            if (!collabService.canView(projectId, userId)) {
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

    /** Save edited YAML — only admin/owner can edit */
    @PostMapping("/project/{projectId}/save")
    public ResponseEntity<ApiResponse<?>> saveEdited(@PathVariable Long projectId,
                                                      @RequestBody Map<String, String> body,
                                                      Authentication auth) {
        try {
            Long userId = (Long) auth.getPrincipal();
            Project project = projectService.getProject(projectId);
            if (!collabService.canEdit(projectId, userId)) {
                return ResponseEntity.status(403).body(ApiResponse.error(403, "无权访问"));
            }
            String yamlContent = body.get("yamlContent");
            if (yamlContent == null || yamlContent.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "内容不能为空"));
            }
            ScriptVersion version = projectService.saveScriptVersion(projectId, yamlContent);
            return ResponseEntity.ok(ApiResponse.success(ScriptVersionResponse.from(version)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
