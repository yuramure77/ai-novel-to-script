package com.scripttool.model.dto;

import com.scripttool.model.entity.Project;
import java.time.LocalDateTime;

public class ProjectResponse {

    private Long id;
    private String title;
    private Integer chapterCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse from(Project project) {
        ProjectResponse response = new ProjectResponse();
        response.id = project.getId();
        response.title = project.getTitle();
        response.chapterCount = project.getChapterCount();
        response.status = project.getStatus().name();
        response.createdAt = project.getCreatedAt();
        response.updatedAt = project.getUpdatedAt();
        return response;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Integer getChapterCount() { return chapterCount; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
