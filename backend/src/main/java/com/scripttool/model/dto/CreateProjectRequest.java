package com.scripttool.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateProjectRequest {

    @NotBlank(message = "项目标题不能为空")
    @Size(max = 200)
    private String title;

    @NotBlank(message = "小说文本不能为空")
    private String originalText;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }
}
