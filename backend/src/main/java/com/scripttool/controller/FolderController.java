package com.scripttool.controller;

import com.scripttool.model.dto.ApiResponse;
import com.scripttool.model.entity.Folder;
import com.scripttool.repository.FolderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderRepository folderRepo;

    public FolderController(FolderRepository folderRepo) { this.folderRepo = folderRepo; }

    private Long uid(Authentication a) { return (Long) a.getPrincipal(); }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> list(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(folderRepo.findByUserIdOrderByNameAsc(uid(auth))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@RequestBody Map<String, String> body, Authentication auth) {
        String name = body.get("name");
        if (name == null || name.isBlank()) return ResponseEntity.badRequest().body(ApiResponse.error(400, "名称不能为空"));
        Folder f = folderRepo.save(new Folder(uid(auth), name.trim()));
        return ResponseEntity.ok(ApiResponse.success(f));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> rename(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        Folder f = folderRepo.findById(id).orElse(null);
        if (f == null || !f.getUserId().equals(uid(auth))) return ResponseEntity.status(403).body(ApiResponse.error(403, "无权操作"));
        f.setName(body.getOrDefault("name", f.getName()));
        if (body.containsKey("color")) f.setColor(body.get("color"));
        folderRepo.save(f);
        return ResponseEntity.ok(ApiResponse.success(f));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable Long id, Authentication auth) {
        Folder f = folderRepo.findById(id).orElse(null);
        if (f == null || !f.getUserId().equals(uid(auth))) return ResponseEntity.status(403).body(ApiResponse.error(403, "无权操作"));
        folderRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
