package com.scripttool.controller;

import com.scripttool.model.dto.ApiResponse;
import com.scripttool.model.dto.CreateProjectRequest;
import com.scripttool.model.dto.ProjectResponse;
import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.service.ChapterSplitService;
import com.scripttool.service.ProjectService;
import com.scripttool.service.ScriptService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ScriptService scriptService;

    public ProjectController(ProjectService projectService, ScriptService scriptService) {
        this.projectService = projectService;
        this.scriptService = scriptService;
    }

    private Long getUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> list(
            @RequestParam(required = false) Long folderId, Authentication auth) {
        List<Project> projects = projectService.listUserProjects(getUserId(auth));
        if (folderId != null) {
            projects = projects.stream().filter(p -> folderId.equals(p.getFolderId())).toList();
        }
        List<ProjectResponse> response = projects.stream().map(ProjectResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<ApiResponse<?>> move(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication auth) {
        Project project = projectService.getProject(id);
        if (!project.getUserId().equals(getUserId(auth)))
            return ResponseEntity.status(403).body(ApiResponse.error(403, "无权操作"));
        Object fid = body.get("folderId");
        project.setFolderId(fid != null ? Long.valueOf(fid.toString()) : null);
        return ResponseEntity.ok(ApiResponse.success(ProjectResponse.from(projectService.updateProject(project))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> get(@PathVariable Long id, Authentication auth) {
        try {
            Project project = projectService.getProject(id);
            if (!project.getUserId().equals(getUserId(auth))) {
                return ResponseEntity.status(403).body(ApiResponse.error(403, "无权访问"));
            }
            ProjectResponse pr = ProjectResponse.from(project);
            Map<String, Object> data = new HashMap<>();
            data.put("project", pr);
            data.put("originalText", project.getOriginalText());
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody CreateProjectRequest request,
                                                  Authentication auth) {
        try {
            Project project = projectService.createProject(getUserId(auth), request.getTitle(), request.getOriginalText());
            return ResponseEntity.ok(ApiResponse.success(ProjectResponse.from(project)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<ApiResponse<?>> rename(@PathVariable Long id,
                                                  @RequestBody Map<String, String> body,
                                                  Authentication auth) {
        try {
            Project project = projectService.getProject(id);
            if (!project.getUserId().equals(getUserId(auth))) {
                return ResponseEntity.status(403).body(ApiResponse.error(403, "无权操作"));
            }
            String newTitle = body.get("title");
            if (newTitle == null || newTitle.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "标题不能为空"));
            }
            project.setTitle(newTitle);
            project = projectService.updateProject(project);
            return ResponseEntity.ok(ApiResponse.success(ProjectResponse.from(project)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable Long id, Authentication auth) {
        try {
            Project project = projectService.getProject(id);
            if (!project.getUserId().equals(getUserId(auth))) {
                return ResponseEntity.status(403).body(ApiResponse.error(403, "无权操作"));
            }
            projectService.deleteProject(id);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/{id}/split")
    public ResponseEntity<ApiResponse<?>> splitChapters(@PathVariable Long id, Authentication auth) {
        try {
            Project project = projectService.getProject(id);
            if (!project.getUserId().equals(getUserId(auth))) {
                return ResponseEntity.status(403).body(ApiResponse.error(403, "无权操作"));
            }
            List<ChapterSplitService.ChapterResult> chapters = scriptService.previewChapters(id);
            List<Map<String, Object>> result = chapters.stream().map(c -> {
                Map<String, Object> m = new HashMap<>();
                m.put("number", c.number());
                m.put("title", c.title());
                m.put("wordCount", c.content().length());
                m.put("content", c.content());
                return m;
            }).toList();
            return ResponseEntity.ok(ApiResponse.success(Map.of("totalChapters", chapters.size(), "chapters", result)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<ApiResponse<?>> generate(@PathVariable Long id, Authentication auth) {
        try {
            ScriptVersion version = scriptService.generateScript(id, getUserId(auth));
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "versionId", version.getId(),
                    "versionNumber", version.getVersionNumber(),
                    "yamlContent", version.getYamlContent()
            )));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping(value = "/{id}/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateStream(@PathVariable Long id, Authentication auth,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "0") int limit,
            @RequestParam(defaultValue = "false") boolean resume) {
        return scriptService.generateScriptStream(id, getUserId(auth), start, limit, resume);
    }
}
