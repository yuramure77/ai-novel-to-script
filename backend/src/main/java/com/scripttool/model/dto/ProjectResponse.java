package com.scripttool.model.dto;

import com.scripttool.model.entity.Project;
import java.time.LocalDateTime;

public class ProjectResponse {
    private Long id;
    private String title;
    private Long folderId;
    private Integer chapterCount;
    private String status;
    private boolean isOwner;
    private String permission;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse from(Project p) {
        return from(p, null, null);
    }

    public static ProjectResponse from(Project p, Long currentUserId, String permission) {
        ProjectResponse r = new ProjectResponse();
        r.id = p.getId(); r.title = p.getTitle(); r.folderId = p.getFolderId();
        r.chapterCount = p.getChapterCount(); r.status = p.getStatus().name();
        r.createdAt = p.getCreatedAt(); r.updatedAt = p.getUpdatedAt();
        r.ownerId = p.getUserId();
        r.isOwner = currentUserId != null && p.getUserId().equals(currentUserId);
        r.permission = r.isOwner ? "ADMIN" : (permission != null ? permission : null);
        return r;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Long getFolderId() { return folderId; }
    public Integer getChapterCount() { return chapterCount; }
    public String getStatus() { return status; }
    public boolean isOwner() { return isOwner; }
    public String getPermission() { return permission; }
    public Long getOwnerId() { return ownerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
