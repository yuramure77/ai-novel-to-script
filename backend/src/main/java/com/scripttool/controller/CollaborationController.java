package com.scripttool.controller;

import com.scripttool.model.dto.ApiResponse;
import com.scripttool.model.entity.Collaboration;
import com.scripttool.model.entity.Project;
import com.scripttool.service.CollaborationService;
import com.scripttool.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class CollaborationController {

    private final CollaborationService collabService;
    private final ProjectService projectService;

    public CollaborationController(CollaborationService collabService, ProjectService projectService) {
        this.collabService = collabService;
        this.projectService = projectService;
    }

    private Long getUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    // ── Collaborators CRUD ──

    @GetMapping("/projects/{id}/collaborators")
    public ResponseEntity<ApiResponse<?>> list(@PathVariable Long id, Authentication auth) {
        try {
            List<Map<String, Object>> result = collabService.listCollaborators(id, getUserId(auth));
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/projects/{id}/collaborators")
    public ResponseEntity<ApiResponse<?>> add(@PathVariable Long id, Authentication auth,
                                               @RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String permStr = body.getOrDefault("permission", "READ");
            Collaboration.Permission permission = Collaboration.Permission.valueOf(permStr.toUpperCase());
            Map<String, Object> result = collabService.addCollaborator(id, getUserId(auth), username, permission);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "无效权限: " + body.get("permission")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PutMapping("/projects/{id}/collaborators/{collabId}")
    public ResponseEntity<ApiResponse<?>> update(@PathVariable Long id, @PathVariable Long collabId,
                                                  Authentication auth, @RequestBody Map<String, String> body) {
        try {
            String permStr = body.get("permission");
            Collaboration.Permission permission = Collaboration.Permission.valueOf(permStr.toUpperCase());
            Map<String, Object> result = collabService.updatePermission(id, getUserId(auth), collabId, permission);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "无效权限: " + body.get("permission")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @DeleteMapping("/projects/{id}/collaborators/{collabId}")
    public ResponseEntity<ApiResponse<?>> remove(@PathVariable Long id, @PathVariable Long collabId,
                                                  Authentication auth) {
        try {
            collabService.removeCollaborator(id, getUserId(auth), collabId);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/projects/shared")
    public ResponseEntity<ApiResponse<?>> shared(Authentication auth) {
        var projects = collabService.getSharedProjects(getUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    // ── Invite link ──

    /** Generate or regenerate invite link for a project */
    @PostMapping("/projects/{id}/invite-link")
    public ResponseEntity<ApiResponse<?>> generateInviteLink(@PathVariable Long id, Authentication auth) {
        try {
            Project project = projectService.getProject(id);
            if (!collabService.canEdit(id, getUserId(auth))) {
                return ResponseEntity.status(403).body(ApiResponse.error(403, "无权操作"));
            }
            if (project.getInviteToken() == null || project.getInviteToken().isEmpty()) {
                project.setInviteToken(UUID.randomUUID().toString());
                projectService.updateProject(project);
            }
            return ResponseEntity.ok(ApiResponse.success(Map.of("inviteToken", project.getInviteToken())));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /** Regenerate invite link (invalidates old one) */
    @PostMapping("/projects/{id}/invite-link/regenerate")
    public ResponseEntity<ApiResponse<?>> regenerateInviteLink(@PathVariable Long id, Authentication auth) {
        try {
            Project project = projectService.getProject(id);
            if (!collabService.canEdit(id, getUserId(auth))) {
                return ResponseEntity.status(403).body(ApiResponse.error(403, "无权操作"));
            }
            project.setInviteToken(UUID.randomUUID().toString());
            projectService.updateProject(project);
            return ResponseEntity.ok(ApiResponse.success(Map.of("inviteToken", project.getInviteToken())));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /** Join a project via invite token */
    @PostMapping("/collaborations/join")
    public ResponseEntity<ApiResponse<?>> join(@RequestParam String token, Authentication auth) {
        try {
            Map<String, Object> result = collabService.joinByToken(token, getUserId(auth));
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ── Active users presence ──

    /** Update last-seen timestamp for current user on this project */
    @PostMapping("/projects/{id}/presence")
    public ResponseEntity<ApiResponse<?>> presence(@PathVariable Long id, Authentication auth) {
        collabService.updatePresence(id, getUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }

    /** Get users active in the last 2 minutes */
    @GetMapping("/projects/{id}/active-users")
    public ResponseEntity<ApiResponse<?>> activeUsers(@PathVariable Long id, Authentication auth) {
        List<Map<String, Object>> users = collabService.getActiveUsers(id, getUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(users));
    }
}
