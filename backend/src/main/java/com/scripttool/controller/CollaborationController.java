package com.scripttool.controller;

import com.scripttool.model.dto.ApiResponse;
import com.scripttool.model.entity.Collaboration;
import com.scripttool.service.CollaborationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class CollaborationController {

    private final CollaborationService collabService;

    public CollaborationController(CollaborationService collabService) {
        this.collabService = collabService;
    }

    private Long getUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    /** List collaborators for a project */
    @GetMapping("/{id}/collaborators")
    public ResponseEntity<ApiResponse<?>> list(@PathVariable Long id, Authentication auth) {
        try {
            List<Map<String, Object>> result = collabService.listCollaborators(id, getUserId(auth));
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /** Add a collaborator by username */
    @PostMapping("/{id}/collaborators")
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

    /** Update collaborator permission */
    @PutMapping("/{id}/collaborators/{collabId}")
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

    /** Remove a collaborator */
    @DeleteMapping("/{id}/collaborators/{collabId}")
    public ResponseEntity<ApiResponse<?>> remove(@PathVariable Long id, @PathVariable Long collabId,
                                                  Authentication auth) {
        try {
            collabService.removeCollaborator(id, getUserId(auth), collabId);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /** List projects shared with current user */
    @GetMapping("/shared")
    public ResponseEntity<ApiResponse<?>> shared(Authentication auth) {
        var projects = collabService.getSharedProjects(getUserId(auth));
        return ResponseEntity.ok(ApiResponse.success(projects));
    }
}
