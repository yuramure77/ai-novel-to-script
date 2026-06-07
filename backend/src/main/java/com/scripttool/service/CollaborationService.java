package com.scripttool.service;

import com.scripttool.model.entity.Collaboration;
import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.User;
import com.scripttool.repository.CollaborationRepository;
import com.scripttool.repository.ProjectRepository;
import com.scripttool.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CollaborationService {

    private final CollaborationRepository collabRepo;
    private final ProjectRepository projectRepo;
    private final UserRepository userRepo;

    public CollaborationService(CollaborationRepository collabRepo,
                                 ProjectRepository projectRepo,
                                 UserRepository userRepo) {
        this.collabRepo = collabRepo;
        this.projectRepo = projectRepo;
        this.userRepo = userRepo;
    }

    /**
     * Check if a user has permission to access a project (owner or collaborator).
     * Returns the effective permission level, or null if no access.
     */
    public Collaboration.Permission getEffectivePermission(Long projectId, Long userId) {
        Project project = projectRepo.findById(projectId).orElse(null);
        if (project == null) return null;

        // Owner has full ADMIN access
        if (project.getUserId().equals(userId)) {
            return Collaboration.Permission.ADMIN;
        }

        // Check collaboration record
        return collabRepo.findByProjectIdAndUserId(projectId, userId)
                .map(Collaboration::getPermission)
                .orElse(null);
    }

    /**
     * Check if user can edit (ADMIN or owner).
     */
    public boolean canEdit(Long projectId, Long userId) {
        Collaboration.Permission perm = getEffectivePermission(projectId, userId);
        return perm == Collaboration.Permission.ADMIN;
    }

    /**
     * Check if user can view (any permission).
     */
    public boolean canView(Long projectId, Long userId) {
        return getEffectivePermission(projectId, userId) != null;
    }

    /**
     * List collaborators for a project with user info.
     */
    public List<Map<String, Object>> listCollaborators(Long projectId, Long ownerId) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("项目不存在"));
        if (!project.getUserId().equals(ownerId)) {
            throw new RuntimeException("只有项目拥有者可以管理协作者");
        }

        return collabRepo.findByProjectId(projectId).stream().map(c -> {
            User user = userRepo.findById(c.getUserId()).orElse(null);
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("userId", c.getUserId());
            m.put("username", user != null ? user.getUsername() : "未知用户");
            m.put("nickname", user != null ? user.getNickname() : "");
            m.put("permission", c.getPermission().name());
            m.put("createdAt", c.getCreatedAt());
            return m;
        }).toList();
    }

    /**
     * Add a collaborator by username.
     */
    @Transactional
    public Map<String, Object> addCollaborator(Long projectId, Long ownerId,
                                                String username, Collaboration.Permission permission) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("项目不存在"));
        if (!project.getUserId().equals(ownerId)) {
            throw new RuntimeException("只有项目拥有者可以添加协作者");
        }

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + username));

        if (user.getId().equals(ownerId)) {
            throw new RuntimeException("不能添加自己为协作者");
        }

        if (collabRepo.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw new RuntimeException("该用户已是协作者");
        }

        Collaboration collab = collabRepo.save(
                new Collaboration(projectId, user.getId(), permission));

        Map<String, Object> m = new HashMap<>();
        m.put("id", collab.getId());
        m.put("userId", collab.getUserId());
        m.put("username", user.getUsername());
        m.put("nickname", user.getNickname());
        m.put("permission", collab.getPermission().name());
        return m;
    }

    /**
     * Update collaborator permission.
     */
    @Transactional
    public Map<String, Object> updatePermission(Long projectId, Long ownerId,
                                                 Long collabId, Collaboration.Permission permission) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("项目不存在"));
        if (!project.getUserId().equals(ownerId)) {
            throw new RuntimeException("只有项目拥有者可以修改权限");
        }

        Collaboration collab = collabRepo.findById(collabId)
                .orElseThrow(() -> new RuntimeException("协作记录不存在"));
        if (!collab.getProjectId().equals(projectId)) {
            throw new RuntimeException("协作记录不属于该项目");
        }

        collab.setPermission(permission);
        collab = collabRepo.save(collab);

        Map<String, Object> m = new HashMap<>();
        m.put("id", collab.getId());
        m.put("permission", collab.getPermission().name());
        return m;
    }

    /**
     * Remove a collaborator.
     */
    @Transactional
    public void removeCollaborator(Long projectId, Long ownerId, Long collabId) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("项目不存在"));
        if (!project.getUserId().equals(ownerId)) {
            throw new RuntimeException("只有项目拥有者可以移除协作者");
        }

        Collaboration collab = collabRepo.findById(collabId)
                .orElseThrow(() -> new RuntimeException("协作记录不存在"));
        if (!collab.getProjectId().equals(projectId)) {
            throw new RuntimeException("协作记录不属于该项目");
        }

        collabRepo.delete(collab);
    }

    /**
     * Get projects shared with a user.
     */
    public List<Project> getSharedProjects(Long userId) {
        List<Collaboration> collabs = collabRepo.findByUserId(userId);
        return collabs.stream()
                .map(c -> projectRepo.findById(c.getProjectId()).orElse(null))
                .filter(p -> p != null)
                .toList();
    }
}
