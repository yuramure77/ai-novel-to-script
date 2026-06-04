package com.scripttool.service;

import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ScriptService {

    private static final Logger log = LoggerFactory.getLogger(ScriptService.class);

    private final ProjectService projectService;
    private final ChapterSplitService chapterSplitService;
    private final ScriptGenService scriptGenService;
    private final YamlGeneratorService yamlGeneratorService;
    private final UserService userService;

    public ScriptService(ProjectService projectService,
                         ChapterSplitService chapterSplitService,
                         ScriptGenService scriptGenService,
                         YamlGeneratorService yamlGeneratorService,
                         UserService userService) {
        this.projectService = projectService;
        this.chapterSplitService = chapterSplitService;
        this.scriptGenService = scriptGenService;
        this.yamlGeneratorService = yamlGeneratorService;
        this.userService = userService;
    }

    /**
     * Execute the full pipeline: split → analyze → generate YAML
     */
    @Transactional
    public ScriptVersion generateScript(Long projectId, Long userId) {
        Project project = projectService.getProject(projectId);

        // Verify ownership
        if (!project.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此项目");
        }

        projectService.updateStatus(projectId, Project.ProjectStatus.PROCESSING);

        try {
            // Step 1: Split chapters
            List<ChapterSplitService.ChapterResult> chapters =
                    chapterSplitService.split(project.getOriginalText());

            if (chapters.isEmpty()) {
                throw new RuntimeException("未能识别出章节，请检查文本格式");
            }

            projectService.updateChapterCount(projectId, chapters.size());

            // Step 2: AI generation
            ScriptGenService.ScriptResult result =
                    scriptGenService.generateScript(project.getOriginalText(), chapters);

            // Step 3: Get user info for author field
            User user = userService.getById(userId);

            // Step 4: Generate YAML
            String yaml = yamlGeneratorService.generate(
                    project.getTitle(),
                    "原著小说",
                    user.getNickname() != null ? user.getNickname() : user.getUsername(),
                    result.characters(),
                    result.scenes()
            );

            // Step 5: Save script version
            ScriptVersion version = projectService.saveScriptVersion(projectId, yaml);

            // Step 6: Mark project as completed
            projectService.updateStatus(projectId, Project.ProjectStatus.COMPLETED);

            return version;

        } catch (Exception e) {
            log.error("Script generation failed for project {}", projectId, e);
            projectService.updateStatus(projectId, Project.ProjectStatus.DRAFT);
            throw new RuntimeException("剧本生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * Preview the split result (without calling AI)
     */
    public List<ChapterSplitService.ChapterResult> previewChapters(Long projectId) {
        Project project = projectService.getProject(projectId);
        return chapterSplitService.split(project.getOriginalText());
    }
}
