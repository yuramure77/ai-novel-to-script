package com.scripttool.service;

import com.scripttool.model.entity.GenerationProgress;
import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.model.entity.User;
import com.scripttool.repository.GenerationProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ScriptService {

    private static final Logger log = LoggerFactory.getLogger(ScriptService.class);
    private static final int CHUNK_SIZE = 1000; // characters per chunk
    private static final ExecutorService sceneImgExecutor = Executors.newFixedThreadPool(4);

    private final ProjectService projectService;
    private final ChapterSplitService chapterSplitService;
    private final ScriptGenService scriptGenService;
    private final YamlGeneratorService yamlGeneratorService;
    private final UserService userService;
    private final GenerationProgressRepository progressRepo;
    private final CollaborationService collabService;
    private final ImageService imageService;

    public ScriptService(ProjectService projectService,
                         ChapterSplitService chapterSplitService,
                         ScriptGenService scriptGenService,
                         YamlGeneratorService yamlGeneratorService,
                         UserService userService,
                         GenerationProgressRepository progressRepo,
                         CollaborationService collabService,
                         ImageService imageService) {
        this.projectService = projectService;
        this.chapterSplitService = chapterSplitService;
        this.scriptGenService = scriptGenService;
        this.yamlGeneratorService = yamlGeneratorService;
        this.userService = userService;
        this.progressRepo = progressRepo;
        this.collabService = collabService;
        this.imageService = imageService;
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
     * Generate with SSE streaming progress + chunk checkpoint.
     * Text split into 1000-char blocks. Each block tracked via bitmap in DB.
     * On restart, shows existing data immediately then resumes from first incomplete chunk.
     * Scene images generated incrementally via background threads and pushed via SSE.
     */
    public SseEmitter generateScriptStream(Long projectId, Long userId, int start, int limit, boolean resume) {
        SseEmitter emitter = new SseEmitter(7_200_000L); // 2 hour timeout
        SecurityContext ctx = SecurityContextHolder.getContext();

        new Thread(() -> {
            SecurityContextHolder.setContext(ctx);
            try {
                Project project = projectService.getProject(projectId);
                // Use collaboration-aware permission check
                if (!collabService.canEdit(projectId, userId) && !project.getUserId().equals(userId)) {
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

                int total = chapters.size();
                projectService.updateChapterCount(projectId, total);

                // --- Chunk indexing ---
                String fullText = project.getOriginalText();
                int totalChunks = Math.max(1, (fullText.length() + CHUNK_SIZE - 1) / CHUNK_SIZE);
                int[] chapterStartChunk = new int[total];
                int[] chapterEndChunk = new int[total];
                int globalPos = 0;
                for (int c = 0; c < total; c++) {
                    chapterStartChunk[c] = globalPos / CHUNK_SIZE;
                    int chapLen = chapters.get(c).content().length();
                    int chapStart = fullText.indexOf(chapters.get(c).content(), globalPos);
                    if (chapStart < 0) chapStart = globalPos;
                    globalPos = chapStart + chapLen;
                    chapterEndChunk[c] = Math.min(totalChunks - 1, (globalPos - 1) / CHUNK_SIZE);
                    if (chapterEndChunk[c] < chapterStartChunk[c])
                        chapterEndChunk[c] = chapterStartChunk[c];
                }

                // --- Checkpoint lookup with correct version handling ---
                GenerationProgress cp = progressRepo.findByProjectId(projectId).orElse(null);

                // Staleness check: only delete if totalChunks changed (text was edited)
                // or if checkpoint version doesn't match the in-progress generation.
                // Note: saveScriptVersion auto-increments (count+1), so partial saves
                // during generation bump the version number. We must use the checkpoint's
                // own versionNumber to identify in-progress work.
                if (cp != null) {
                    if (cp.getTotalChunks() != totalChunks) {
                        // Text was edited since last checkpoint — discard and restart
                        log.info("Checkpoint totalChunks {} != current {}, discarding stale checkpoint",
                            cp.getTotalChunks(), totalChunks);
                        progressRepo.deleteByProjectId(projectId);
                        cp = null;
                    } else if (cp.isComplete() && cp.getPartialYaml() != null
                            && !cp.getPartialYaml().isBlank()) {
                        // All chunks done with valid YAML — return cached result
                        send(emitter, "progress", Map.of("step", "done", "message",
                            "剧本已全部生成完毕", "percent", 100));
                        send(emitter, "done", Map.of(
                            "yamlContent", cp.getPartialYaml(),
                            "versionNumber", cp.getVersionNumber(),
                            "totalChapters", total,
                            "totalChunks", totalChunks,
                            "fromCache", true
                        ));
                        projectService.updateStatus(projectId, Project.ProjectStatus.COMPLETED);
                        emitter.complete();
                        return;
                    }
                }

                // Determine version for this generation run
                final int runVersion;
                if (cp != null) {
                    // Resume in-progress generation — keep checkpoint's version
                    runVersion = cp.getVersionNumber();
                } else {
                    runVersion = projectService.getLatestScriptVersion(projectId) != null ?
                        projectService.getLatestScriptVersion(projectId).getVersionNumber() + 1 : 1;
                    cp = new GenerationProgress(projectId, totalChunks, runVersion);
                    cp = progressRepo.save(cp);
                }

                // Find first incomplete chunk → map to chapter index
                int firstIncomplete = cp.firstIncompleteChunk();
                final int resumeChapter;
                int rc = 0;
                for (int c = 0; c < total; c++) {
                    if (chapterEndChunk[c] >= firstIncomplete) {
                        rc = c;
                        break;
                    }
                }
                resumeChapter = rc;

                // --- Send existing partial YAML immediately on resume ---
                final String oldPartialYaml = resumeChapter > 0 && cp.getPartialYaml() != null
                    && !cp.getPartialYaml().isBlank() ? cp.getPartialYaml() : null;

                if (oldPartialYaml != null) {
                    // Show existing data right away so user sees something immediately
                    ScriptVersion existingVersion = projectService.getLatestScriptVersion(projectId);
                    send(emitter, "resume_data", data(
                        "yamlContent", oldPartialYaml,
                        "versionNumber", existingVersion != null ? existingVersion.getVersionNumber() : runVersion,
                        "versionId", existingVersion != null ? existingVersion.getId() : 0,
                        "resumeFrom", resumeChapter,
                        "completedChunks", cp.completedCount(),
                        "totalChunks", totalChunks,
                        "totalChapters", total,
                        "message", "已加载历史进度，续写中..."
                    ));
                }

                // --- Resume / skip logic ---
                List<ChapterSplitService.ChapterResult> toProcess;
                if (resumeChapter > 0) {
                    toProcess = chapters.subList(resumeChapter, total);
                    send(emitter, "progress", data("step", "resume", "message",
                        "断点续传: " + cp.completedCount() + "/" + totalChunks + " 块已完成，"
                        + "从第 " + (resumeChapter + 1) + " 章续写",
                        "resumeFrom", resumeChapter,
                        "completedChunks", cp.completedCount(),
                        "totalChunks", totalChunks,
                        "totalChapters", toProcess.size(),
                        "fullTotal", total));
                } else {
                    toProcess = chapters.subList(0, total);
                    send(emitter, "progress", Map.of("step", "split", "message",
                        "识别到 " + total + " 章，" + totalChunks + " 个块，开始生成...",
                        "totalChapters", total, "totalChunks", totalChunks));
                }

                // Estimate remaining chunks for progress
                int remainChunks = 0;
                for (var ch : toProcess)
                    remainChunks += Math.max(1, (ch.content().length() + CHUNK_SIZE - 1) / CHUNK_SIZE);
                send(emitter, "progress", Map.of("step", "split", "message",
                    "待处理 " + remainChunks + " 个块", "percent", 0,
                    "totalChunks", totalChunks, "doneChunks", cp.completedCount()));

                User user = userService.getById(userId);
                final ScriptVersion[] savedVersion = {null};
                final GenerationProgress fcp = cp;
                // Track which scene indices have been sent for image generation
                final Set<Integer> imgGenDispatched = Collections.synchronizedSet(new HashSet<>());
                final AtomicInteger sceneIdxCounter = new AtomicInteger(0);

                // --- Generate ---
                ScriptGenService.ScriptResult result = scriptGenService.generate(
                        project.getOriginalText(), toProcess,
                        // Progress callback
                        (d, t, msg) -> {
                            int pct = t > 0 ? (int)((double)d / t * 90) : 50;
                            send(emitter, "progress", Map.of(
                                "step", "ai", "message", msg, "percent", pct,
                                "done", d, "total", t
                            ));
                        },
                        // Chapter-done callback — update checkpoint + trigger scene images
                        (chars, scenes, d, t) -> {
                            String partialYaml = yamlGeneratorService.generate(
                                    project.getTitle(), "原著小说",
                                    user.getNickname() != null ? user.getNickname() : user.getUsername(),
                                    chars, scenes);

                            // Merge with old partial YAML when resuming
                            if (oldPartialYaml != null && !oldPartialYaml.isBlank()) {
                                partialYaml = mergeYaml(oldPartialYaml, partialYaml);
                            }

                            savedVersion[0] = projectService.saveScriptVersion(projectId, partialYaml);

                            // Mark this chapter's chunks as done
                            int chapIdx = resumeChapter + (d - 1);
                            if (chapIdx < total) {
                                try {
                                    fcp.markRangeDone(chapterStartChunk[chapIdx], chapterEndChunk[chapIdx]);
                                } catch (Exception ex) {
                                    log.warn("markRangeDone failed for chapter {}: {}", chapIdx, ex.getMessage());
                                }
                            }
                            fcp.setPartialYaml(partialYaml);
                            progressRepo.save(fcp);

                            // --- Incremental scene image generation ---
                            // Dispatch background threads for new scenes in this chapter
                            if (scenes != null && !scenes.isEmpty()) {
                                for (int si = 0; si < scenes.size(); si++) {
                                    final int sceneIdx = si;
                                    if (!imgGenDispatched.contains(sceneIdx)) {
                                        imgGenDispatched.add(sceneIdx);
                                        final Map<String, Object> s = scenes.get(si);
                                        sceneImgExecutor.submit(() -> {
                                            try {
                                                String desc = Objects.toString(s.get("description"), "");
                                                String location = Objects.toString(s.get("location"), "");
                                                String time = Objects.toString(s.get("time"), "");
                                                String mood = Objects.toString(s.get("mood"), "");
                                                String stitle = Objects.toString(s.get("title"),
                                                    desc.isBlank() ? "场景" + (sceneIdx + 1) : "场景" + (sceneIdx + 1));

                                                Map<String, Object> img = imageService.generateSceneImage(
                                                    projectId, sceneIdx, desc, location, time, mood);
                                                send(emitter, "scene_image", data(
                                                    "index", sceneIdx,
                                                    "url", img.get("url"),
                                                    "prompt", img.get("prompt"),
                                                    "title", stitle
                                                ));
                                                log.info("Scene image generated idx={}, title={}", sceneIdx, stitle);
                                            } catch (Exception ex) {
                                                log.warn("Scene image gen failed idx={}: {}", sceneIdx, ex.getMessage());
                                            }
                                        });
                                    }
                                }
                            }

                            int pct = t > 0 ? (int)((double)d / t * 90) : 50;
                            int doneC = fcp.completedCount();
                            send(emitter, "chapter_done", data(
                                "yamlContent", partialYaml,
                                "versionId", savedVersion[0].getId(),
                                "versionNumber", savedVersion[0].getVersionNumber(),
                                "done", d, "total", t,
                                "charCount", chars.size(),
                                "sceneCount", scenes.size(),
                                "isLast", d >= t,
                                "percent", pct,
                                "completedChunks", doneC,
                                "totalChunks", totalChunks
                            ));
                        });

                // --- Finalize ---
                int charCount = result.characters().size();
                int sceneCount = result.scenes().size();
                String finalYaml = savedVersion[0] != null ?
                    yamlGeneratorService.generate(project.getTitle(), "原著小说",
                        user.getNickname() != null ? user.getNickname() : user.getUsername(),
                        result.characters(), result.scenes()) : "";

                // Merge with old partial YAML if resuming
                if (oldPartialYaml != null && finalYaml != null && !finalYaml.isBlank()) {
                    finalYaml = mergeYaml(oldPartialYaml, finalYaml);
                }

                projectService.saveScriptVersion(projectId, finalYaml);
                progressRepo.deleteByProjectId(projectId); // cleanup checkpoint
                projectService.updateStatus(projectId, Project.ProjectStatus.COMPLETED);

                send(emitter, "done", data(
                        "versionId", savedVersion[0] != null ? savedVersion[0].getId() : 0,
                        "versionNumber", savedVersion[0] != null ? savedVersion[0].getVersionNumber() : 1,
                        "yamlContent", finalYaml,
                        "charCount", charCount,
                        "sceneCount", sceneCount,
                        "totalChapters", total,
                        "totalChunks", totalChunks
                ));
                emitter.complete();

            } catch (Exception e) {
                log.error("Generation failed", e);
                send(emitter, "error", e.getMessage());
                emitter.complete();
            } finally {
                SecurityContextHolder.clearContext();
            }
        }).start();

        return emitter;
    }

    /** Merge old partial YAML scenes into new YAML — preserves previously generated chapters */
    private String mergeYaml(String oldYaml, String newYaml) {
        try {
            // Extract scenes section from both YAMLs
            String oldScenes = extractSection(oldYaml, "scenes:");
            String newScenes = extractSection(newYaml, "scenes:");
            if (oldScenes.isEmpty() || newScenes.isEmpty()) return newYaml;

            // Collect scene IDs from new YAML to avoid duplicates
            java.util.Set<String> newIds = new java.util.HashSet<>();
            var idPat = java.util.regex.Pattern.compile("^\\s*-?\\s*id:\\s*(\\S+)", java.util.regex.Pattern.MULTILINE);
            var m = idPat.matcher(newScenes);
            while (m.find()) newIds.add(m.group(1));

            // Filter old scenes — keep only those NOT in new YAML
            StringBuilder mergedScenes = new StringBuilder(newScenes.stripTrailing());
            String[] oldLines = oldScenes.split("\n");
            boolean inOldScene = false;
            StringBuilder currentOldScene = new StringBuilder();
            String currentOldId = null;
            for (String line : oldLines) {
                var idm = idPat.matcher(line);
                if (idm.find()) {
                    // Flush previous scene
                    if (currentOldId != null && !newIds.contains(currentOldId) && currentOldScene.length() > 0) {
                        mergedScenes.append("\n").append(currentOldScene.toString().stripTrailing());
                    }
                    currentOldScene = new StringBuilder();
                    currentOldId = idm.group(1);
                    inOldScene = true;
                }
                if (inOldScene) currentOldScene.append(line).append("\n");
            }
            // Flush last scene
            if (currentOldId != null && !newIds.contains(currentOldId) && currentOldScene.length() > 0) {
                mergedScenes.append("\n").append(currentOldScene.toString().stripTrailing());
            }

            // Replace scenes section in new YAML
            return newYaml.replace(newScenes, mergedScenes.toString());
        } catch (Exception e) {
            log.warn("YAML merge failed: {}", e.getMessage());
            return newYaml;
        }
    }

    private String extractSection(String yaml, String header) {
        int start = yaml.indexOf(header);
        if (start < 0) return "";
        int contentStart = yaml.indexOf('\n', start) + 1;
        // Find next top-level key (unindented)
        var nextKey = java.util.regex.Pattern.compile("^\\w", java.util.regex.Pattern.MULTILINE);
        var m = nextKey.matcher(yaml);
        int end = yaml.length();
        if (m.find(contentStart)) end = m.start();
        return yaml.substring(start, end);
    }

    /** Quick map builder for SSE event data — avoids Map.of's 10-arg limit */
    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
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
