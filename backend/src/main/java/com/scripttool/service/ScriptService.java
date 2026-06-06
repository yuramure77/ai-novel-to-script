package com.scripttool.service;

import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    @Transactional
    public ScriptVersion generateScript(Long projectId, Long userId) {
        Project project = projectService.getProject(projectId);
        if (!project.getUserId().equals(userId)) throw new RuntimeException("无权操作此项目");
        projectService.updateStatus(projectId, Project.ProjectStatus.PROCESSING);

        try {
            List<ChapterSplitService.ChapterResult> chapters = chapterSplitService.split(project.getOriginalText());
            if (chapters.isEmpty()) throw new RuntimeException("未能识别出章节");

            projectService.updateChapterCount(projectId, chapters.size());

            ScriptGenService.ScriptResult result = scriptGenService.generateScript(
                    project.getOriginalText(), chapters, p -> {});
            User user = userService.getById(userId);

            String yaml = yamlGeneratorService.generate(
                    project.getTitle(), "原著小说",
                    user.getNickname() != null ? user.getNickname() : user.getUsername(),
                    result.characters(), result.scenes());

            ScriptVersion version = projectService.saveScriptVersion(projectId, yaml);
            projectService.updateStatus(projectId, Project.ProjectStatus.COMPLETED);
            return version;

        } catch (Exception e) {
            log.error("Script generation failed for project {}", projectId, e);
            projectService.updateStatus(projectId, Project.ProjectStatus.DRAFT);
            throw new RuntimeException("剧本生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * Generate with SSE streaming progress
     */
    public SseEmitter generateScriptStream(Long projectId, Long userId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        SecurityContext ctx = SecurityContextHolder.getContext();

        new Thread(() -> {
            SecurityContextHolder.setContext(ctx);
            try {
                Project project = projectService.getProject(projectId);
                if (!project.getUserId().equals(userId)) {
                    send(emitter, "error", "无权操作此项目");
                    emitter.complete();
                    return;
                }

                projectService.updateStatus(projectId, Project.ProjectStatus.PROCESSING);
                send(emitter, "progress", Map.of("step", "split", "message", "正在分章..."));

                List<ChapterSplitService.ChapterResult> chapters = chapterSplitService.split(project.getOriginalText());
                if (chapters.isEmpty()) {
                    send(emitter, "error", "未能识别出章节");
                    emitter.complete();
                    return;
                }

                projectService.updateChapterCount(projectId, chapters.size());
                send(emitter, "progress", Map.of("step", "split", "message", "识别到 " + chapters.size() + " 个章节", "totalChapters", chapters.size()));

                // Extract characters + generate scenes with progress
                send(emitter, "progress", Map.of("step", "ai", "message", "AI 分析中...", "percent", 10));

                ScriptGenService.ScriptResult result = scriptGenService.generateScript(
                        project.getOriginalText(), chapters,
                        pct -> send(emitter, "progress", Map.of("step", "ai", "message", "AI 分析中...", "percent", pct))
                );

                int charCount = result.characters().size();
                int sceneCount = result.scenes().size();
                send(emitter, "progress", Map.of("step", "ai", "message", "提取到 " + charCount + " 个角色，" + sceneCount + " 个场景", "percent", 90));

                // Generate YAML
                send(emitter, "progress", Map.of("step", "yaml", "message", "生成 YAML 剧本...", "percent", 95));

                User user = userService.getById(userId);
                String yaml = yamlGeneratorService.generate(
                        project.getTitle(), "原著小说",
                        user.getNickname() != null ? user.getNickname() : user.getUsername(),
                        result.characters(), result.scenes());

                ScriptVersion version = projectService.saveScriptVersion(projectId, yaml);
                projectService.updateStatus(projectId, Project.ProjectStatus.COMPLETED);

                send(emitter, "done", Map.of(
                        "versionId", version.getId(),
                        "versionNumber", version.getVersionNumber(),
                        "yamlContent", yaml,
                        "charCount", charCount,
                        "sceneCount", sceneCount
                ));
                emitter.complete();

            } catch (Exception e) {
                log.error("Generation failed", e);
                send(emitter, "error", e.getMessage());
                projectService.updateStatus(projectId, Project.ProjectStatus.DRAFT);
                emitter.complete();
            } finally {
                SecurityContextHolder.clearContext();
            }
        }).start();

        return emitter;
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            log.warn("SSE send failed", e);
        }
    }

    public List<ChapterSplitService.ChapterResult> previewChapters(Long projectId) {
        return chapterSplitService.split(projectService.getProject(projectId).getOriginalText());
    }
}
