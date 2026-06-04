package com.scripttool.model.dto;

import com.scripttool.model.entity.ScriptVersion;
import java.time.LocalDateTime;

public class ScriptVersionResponse {

    private Long id;
    private Long projectId;
    private Integer versionNumber;
    private String yamlContent;
    private LocalDateTime createdAt;

    public static ScriptVersionResponse from(ScriptVersion version) {
        ScriptVersionResponse response = new ScriptVersionResponse();
        response.id = version.getId();
        response.projectId = version.getProjectId();
        response.versionNumber = version.getVersionNumber();
        response.yamlContent = version.getYamlContent();
        response.createdAt = version.getCreatedAt();
        return response;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getYamlContent() { return yamlContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
