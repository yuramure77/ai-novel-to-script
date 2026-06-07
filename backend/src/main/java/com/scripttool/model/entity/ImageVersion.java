package com.scripttool.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_versions")
public class ImageVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "image_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ImageType imageType;

    @Column(name = "target_index", nullable = false)
    private Integer targetIndex;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(length = 1024)
    private String prompt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ImageType {
        CHARACTER, SCENE
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public ImageVersion() {}

    public ImageVersion(Long projectId, ImageType imageType, Integer targetIndex,
                         String url, String prompt) {
        this.projectId = projectId;
        this.imageType = imageType;
        this.targetIndex = targetIndex;
        this.url = url;
        this.prompt = prompt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public ImageType getImageType() { return imageType; }
    public void setImageType(ImageType imageType) { this.imageType = imageType; }
    public Integer getTargetIndex() { return targetIndex; }
    public void setTargetIndex(Integer targetIndex) { this.targetIndex = targetIndex; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
