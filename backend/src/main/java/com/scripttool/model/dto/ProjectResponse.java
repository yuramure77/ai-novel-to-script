package com.scripttool.model.dto;

import com.scripttool.model.entity.Project;
import java.time.LocalDateTime;

public class ProjectResponse {
    private Long id;
    private String title;
    private Long folderId;
    private Integer chapterCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse from(Project p) {
        ProjectResponse r = new ProjectResponse();
        r.id = p.getId(); r.title = p.getTitle(); r.folderId = p.getFolderId();
        r.chapterCount = p.getChapterCount(); r.status = p.getStatus().name();
        r.createdAt = p.getCreatedAt(); r.updatedAt = p.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Long getFolderId() { return folderId; }
    public Integer getChapterCount() { return chapterCount; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
