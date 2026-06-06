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

            ScriptGenService.ScriptResult result = scriptGenService.generate(
                    project.getOriginalText(), chapters, null, null);
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
    public SseEmitter generateScriptStream(Long projectId, Long userId, int start, int limit) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout for long novels
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

                // Apply pagination: only process a range of chapters
                int total = chapters.size();
                int from = Math.max(0, Math.min(start, total - 1));
                int to = limit > 0 ? Math.min(from + limit, total) : total;
                List<ChapterSplitService.ChapterResult> page = chapters.subList(from, to);

                projectService.updateChapterCount(projectId, total);
                String pageInfo = limit > 0 ? String.format("识别到 %d 章，处理第 %d-%d 章", total, from + 1, to) :
                        "识别到 " + total + " 个章节";
                send(emitter, "progress", Map.of("step", "split", "message", pageInfo,
                        "totalChapters", page.size(), "fullTotal", total, "pageStart", from, "pageEnd", to));

                // Count total chunks before starting
                int estChunks = 0;
                for (var ch : page) {
                    estChunks += Math.max(1, (ch.content().length() + 499) / 500);
                }
                send(emitter, "progress", Map.of("step", "split", "message",
                    "拆分为 " + estChunks + " 个小块，开始生成...", "percent", 0, "totalChunks", estChunks));

                User user = userService.getById(userId);
                final ScriptVersion[] savedVersion = {null};

                ScriptGenService.ScriptResult result = scriptGenService.generate(
                        project.getOriginalText(), page,
                        // Progress — called before each chunk
                        (d, t, msg) -> {
                            int pct = t > 0 ? (int)((double)d / t * 90) : 50;
                            send(emitter, "progress", Map.of(
                                "step", "ai", "message", msg, "percent", pct,
                                "done", d, "total", t
                            ));
                        },
                        // Chunk callback — called after EACH AI call with accumulated results
                        (chars, scenes, d, t) -> {
                            String partialYaml = yamlGeneratorService.generate(
                                    project.getTitle(), "原著小说",
                                    user.getNickname() != null ? user.getNickname() : user.getUsername(),
                                    chars, scenes);
                            savedVersion[0] = projectService.saveScriptVersion(projectId, partialYaml);
                            int pct = t > 0 ? (int)((double)d / t * 90) : 50;
                            send(emitter, "chapter_done", Map.of(
                                "yamlContent", partialYaml,
                                "versionId", savedVersion[0].getId(),
                                "versionNumber", savedVersion[0].getVersionNumber(),
                                "done", d, "total", t,
                                "charCount", chars.size(),
                                "sceneCount", scenes.size(),
                                "isLast", d >= t,
                                "percent", pct
                            ));
                        });

                int charCount = result.characters().size();
                int sceneCount = result.scenes().size();
                String finalYaml = savedVersion[0] != null ?
                    yamlGeneratorService.generate(project.getTitle(), "原著小说",
                        user.getNickname() != null ? user.getNickname() : user.getUsername(),
                        result.characters(), result.scenes()) : "";
                projectService.saveScriptVersion(projectId, finalYaml);
                projectService.updateStatus(projectId, Project.ProjectStatus.COMPLETED);

                send(emitter, "done", Map.of(
                        "versionId", savedVersion[0] != null ? savedVersion[0].getId() : 0,
                        "versionNumber", savedVersion[0] != null ? savedVersion[0].getVersionNumber() : 1,
                        "yamlContent", finalYaml,
                        "charCount", charCount,
                        "sceneCount", sceneCount,
                        "totalChapters", total
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
