package com.scripttool.service;

import com.scripttool.model.entity.GenerationPlan;
import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.model.entity.User;
import com.scripttool.repository.GenerationPlanRepository;
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

/**
 * Script generation with task-based plan persistence.
 * Flow: chapter split → GenerationPlan (DB) → execute chapter-by-chapter → SSE progress.
 * On interrupt, reload plan from DB and resume from first PENDING chapter.
 */
@Service
public class ScriptService {

    private static final Logger log = LoggerFactory.getLogger(ScriptService.class);
    private static final ExecutorService sceneImgExecutor = Executors.newFixedThreadPool(4);

    private final ProjectService projectService;
    private final ChapterSplitService chapterSplitService;
    private final ScriptGenService scriptGenService;
    private final YamlGeneratorService yamlGeneratorService;
    private final UserService userService;
    private final GenerationPlanRepository planRepo;
    private final CollaborationService collabService;
    private final ImageService imageService;

    public ScriptService(ProjectService projectService,
                         ChapterSplitService chapterSplitService,
                         ScriptGenService scriptGenService,
                         YamlGeneratorService yamlGeneratorService,
                         UserService userService,
                         GenerationPlanRepository planRepo,
                         CollaborationService collabService,
                         ImageService imageService) {
        this.projectService = projectService;
        this.chapterSplitService = chapterSplitService;
        this.scriptGenService = scriptGenService;
        this.yamlGeneratorService = yamlGeneratorService;
        this.userService = userService;
        this.planRepo = planRepo;
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
     * Task-based streaming generation with DB-persisted plan.
     *
     * New flow:
     * 1. Split chapters → create GenerationPlan (each chapter = a task)
     * 2. Push plan via SSE → frontend shows chapter progress bar
     * 3. Execute chapters ONE AT A TIME: PENDING → IN_PROGRESS → DONE
     * 4. After each chapter: save to DB, merge YAML, push SSE
     * 5. On reconnect: load plan from DB → show done chapters → resume from first PENDING
     */
    public SseEmitter generateScriptStream(Long projectId, Long userId, int start, int limit, boolean resume) {
        SseEmitter emitter = new SseEmitter(7_200_000L); // 2 hour timeout
        SecurityContext ctx = SecurityContextHolder.getContext();

        new Thread(() -> {
            SecurityContextHolder.setContext(ctx);
            try {
                Project project = projectService.getProject(projectId);
                if (!collabService.canEdit(projectId, userId) && !project.getUserId().equals(userId)) {
                    send(emitter, "error", "无权操作此项目");
                    emitter.complete();
                    return;
                }

                projectService.updateStatus(projectId, Project.ProjectStatus.PROCESSING);
                send(emitter, "progress", Map.of("step", "split", "message", "正在分章..."));

                // ── ① Chapter split ──
                List<ChapterSplitService.ChapterResult> chapters = chapterSplitService.split(project.getOriginalText());
                if (chapters.isEmpty()) {
                    send(emitter, "error", "未能识别出章节");
                    emitter.complete();
                    return;
                }

                int total = chapters.size();
                projectService.updateChapterCount(projectId, total);

                // ── ② Load or create GenerationPlan ──
                GenerationPlan plan = planRepo.findByProjectId(projectId).orElse(null);

                // Staleness: text changed → chapter count differs → reset plan
                if (plan != null && plan.getTotalChapters() != total) {
                    log.info("Plan chapters {} != current {}, resetting", plan.getTotalChapters(), total);
                    planRepo.deleteByProjectId(projectId);
                    plan = null;
                }

                final boolean isResuming;
                if (plan == null) {
                    // Fresh start — create plan with all chapters PENDING
                    int nextVersion = projectService.getLatestScriptVersion(projectId) != null ?
                        projectService.getLatestScriptVersion(projectId).getVersionNumber() + 1 : 1;
                    List<Map<String, Object>> tasks = new ArrayList<>();
                    for (int i = 0; i < total; i++) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("num", i + 1);
                        t.put("title", chapters.get(i).title());
                        t.put("status", "PENDING");
                        t.put("sceneCount", 0);
                        t.put("charCount", 0);
                        tasks.add(t);
                    }
                    plan = new GenerationPlan(projectId, tasks, nextVersion);
                    plan = planRepo.save(plan);
                    isResuming = false;
                } else {
                    isResuming = plan.getCompletedChapters() > 0;
                }

                // ── ③ Push plan to frontend ──
                send(emitter, "plan", plan.toPlanData());

                // ── ④ If resuming, rebuild and send existing YAML ──
                final String oldPartialYaml;
                if (isResuming && plan.getPartialYaml() != null && !plan.getPartialYaml().isBlank()) {
                    oldPartialYaml = plan.getPartialYaml();
                    ScriptVersion existingVersion = projectService.getLatestScriptVersion(projectId);
                    send(emitter, "resume_data", data(
                        "yamlContent", oldPartialYaml,
                        "versionNumber", existingVersion != null ? existingVersion.getVersionNumber() : plan.getVersionNumber(),
                        "versionId", existingVersion != null ? existingVersion.getId() : 0,
                        "completedChapters", plan.getCompletedChapters(),
                        "totalChapters", total,
                        "chapters", plan.getChapterTasks(),
                        "message", "已加载历史进度: " + plan.getCompletedChapters() + "/" + total + " 章已完成，续写中..."
                    ));
                } else {
                    oldPartialYaml = null;
                }

                // ── ⑤ Find start point ──
                int startChapter = plan.getCompletedChapters(); // 0 if fresh, N if resuming
                if (startChapter > 0) {
                    send(emitter, "progress", data("step", "resume", "message",
                        "断点续传: " + startChapter + "/" + total + " 章已完成，"
                        + "从第 " + (startChapter + 1) + " 章开始",
                        "completedChapters", startChapter,
                        "totalChapters", total,
                        "percent", (int)((double)startChapter / total * 100)));
                } else {
                    send(emitter, "progress", Map.of("step", "split", "message",
                        "规划完成: " + total + " 章待生成，开始逐章处理...",
                        "totalChapters", total));
                }

                User user = userService.getById(userId);
                final String authorName = user.getNickname() != null ? user.getNickname() : user.getUsername();
                final Set<Integer> imgGenDispatched = Collections.synchronizedSet(new HashSet<>());

                // ── ⑥ Execute chapter by chapter ──
                for (int i = startChapter; i < total; i++) {
                    final int chapterIdx = i;
                    ChapterSplitService.ChapterResult ch = chapters.get(i);
                    int chapterNum = i + 1;

                    // Mark IN_PROGRESS
                    plan.markInProgress(chapterIdx);
                    planRepo.save(plan);
                    send(emitter, "chapter_start", data(
                        "chapter", chapterNum,
                        "title", ch.title(),
                        "totalChapters", total,
                        "doneSoFar", chapterIdx,
                        "percent", (int)((double)chapterIdx / total * 100)
                    ));

                    // Generate script for this ONE chapter
                    ScriptGenService.ScriptResult r;
                    try {
                        final int fi = chapterIdx; // effectively final for lambda
                        r = scriptGenService.generate(
                            project.getOriginalText(),
                            List.of(ch),
                            (d, t, msg) -> {
                                int pct = (int)((double)(fi + (d > 0 ? (double)d / t : 0)) / total * 100);
                                send(emitter, "progress", Map.of(
                                    "step", "ai", "message", msg, "percent", Math.min(99, pct),
                                    "done", d, "total", t, "chapter", chapterNum
                                ));
                            },
                            null // We handle YAML generation ourselves, not via callback
                        );
                    } catch (Exception ex) {
                        log.error("Chapter {} generation failed", chapterNum, ex);
                        plan.getChapterTasks().get(i).put("status", "FAILED");
                        planRepo.save(plan);
                        send(emitter, "error", "第" + chapterNum + "章生成失败: " + ex.getMessage());
                        emitter.complete();
                        return;
                    }

                    // Merge characters
                    plan.mergeCharacters(r.characters());

                    // Merge scenes (chapter-aware)
                    for (var s : r.scenes()) {
                        s.putIfAbsent("chapter", chapterNum);
                    }
                    plan.appendScenes(r.scenes());

                    // Build accumulated YAML
                    String accumulatedYaml = yamlGeneratorService.generate(
                        project.getTitle(), "原著小说", authorName,
                        plan.getAccumulatedCharacters(), plan.getAccumulatedScenes());

                    // Merge with old partial YAML if resuming
                    if (oldPartialYaml != null && !oldPartialYaml.isBlank()) {
                        accumulatedYaml = mergeYaml(oldPartialYaml, accumulatedYaml);
                    }

                    plan.setPartialYaml(accumulatedYaml);
                    plan.markDone(i, r.scenes().size(), r.characters().size());
                    planRepo.save(plan);

                    // Save ScriptVersion snapshot
                    ScriptVersion sv = projectService.saveScriptVersion(projectId, accumulatedYaml);

                    // ── Incremental scene image generation ──
                    if (r.scenes() != null && !r.scenes().isEmpty()) {
                        List<Map<String, Object>> allScenes = plan.getAccumulatedScenes();
                        for (int si = 0; si < allScenes.size(); si++) {
                            final int sceneIdx = si;
                            if (!imgGenDispatched.contains(sceneIdx)) {
                                imgGenDispatched.add(sceneIdx);
                                final Map<String, Object> s = allScenes.get(si);
                                sceneImgExecutor.submit(() -> {
                                    try {
                                        String desc = Objects.toString(s.get("description"), "");
                                        String location = Objects.toString(s.get("location"), "");
                                        String time = Objects.toString(s.get("time"), "");
                                        String mood = Objects.toString(s.get("mood"), "");
                                        Map<String, Object> img = imageService.generateSceneImage(
                                            projectId, sceneIdx, desc, location, time, mood);
                                        send(emitter, "scene_image", data(
                                            "index", sceneIdx,
                                            "url", img.get("url"),
                                            "prompt", img.get("prompt"),
                                            "title", desc.isBlank() ? "场景" + (sceneIdx + 1) : desc
                                        ));
                                    } catch (Exception ex) {
                                        log.warn("Scene image gen failed idx={}: {}", sceneIdx, ex.getMessage());
                                    }
                                });
                            }
                        }
                    }

                    int pct = (int)((double)(i + 1) / total * 100);
                    send(emitter, "chapter_done", data(
                        "yamlContent", accumulatedYaml,
                        "versionId", sv.getId(),
                        "versionNumber", sv.getVersionNumber(),
                        "chapter", chapterNum,
                        "totalChapters", total,
                        "completedChapters", plan.getCompletedChapters(),
                        "charCount", r.characters().size(),
                        "sceneCount", r.scenes().size(),
                        "percent", pct,
                        "chapters", plan.getChapterTasks()
                    ));
                }

                // ── ⑦ Finalize ──
                List<Map<String, Object>> finalChars = plan.getAccumulatedCharacters();
                List<Map<String, Object>> finalScenes = plan.getAccumulatedScenes();

                String finalYaml = yamlGeneratorService.generate(
                    project.getTitle(), "原著小说", authorName, finalChars, finalScenes);

                if (oldPartialYaml != null && !oldPartialYaml.isBlank()) {
                    finalYaml = mergeYaml(oldPartialYaml, finalYaml);
                }

                projectService.saveScriptVersion(projectId, finalYaml);
                planRepo.deleteByProjectId(projectId); // cleanup plan
                projectService.updateStatus(projectId, Project.ProjectStatus.COMPLETED);

                send(emitter, "done", data(
                    "yamlContent", finalYaml,
                    "versionNumber", plan.getVersionNumber(),
                    "charCount", finalChars.size(),
                    "sceneCount", finalScenes.size(),
                    "totalChapters", total,
                    "completedChapters", total
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
            String oldScenes = extractSection(oldYaml, "scenes:");
            String newScenes = extractSection(newYaml, "scenes:");
            if (oldScenes.isEmpty() || newScenes.isEmpty()) return newYaml;

            java.util.Set<String> newIds = new java.util.HashSet<>();
            var idPat = java.util.regex.Pattern.compile("^\\s*-?\\s*id:\\s*(\\S+)", java.util.regex.Pattern.MULTILINE);
            var m = idPat.matcher(newScenes);
            while (m.find()) newIds.add(m.group(1));

            StringBuilder mergedScenes = new StringBuilder(newScenes.stripTrailing());
            String[] oldLines = oldScenes.split("\n");
            boolean inOldScene = false;
            StringBuilder currentOldScene = new StringBuilder();
            String currentOldId = null;
            for (String line : oldLines) {
                var idm = idPat.matcher(line);
                if (idm.find()) {
                    if (currentOldId != null && !newIds.contains(currentOldId) && currentOldScene.length() > 0) {
                        mergedScenes.append("\n").append(currentOldScene.toString().stripTrailing());
                    }
                    currentOldScene = new StringBuilder();
                    currentOldId = idm.group(1);
                    inOldScene = true;
                }
                if (inOldScene) currentOldScene.append(line).append("\n");
            }
            if (currentOldId != null && !newIds.contains(currentOldId) && currentOldScene.length() > 0) {
                mergedScenes.append("\n").append(currentOldScene.toString().stripTrailing());
            }

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
        var nextKey = java.util.regex.Pattern.compile("^\\w", java.util.regex.Pattern.MULTILINE);
        var m = nextKey.matcher(yaml);
        int end = yaml.length();
        if (m.find(contentStart)) end = m.start();
        return yaml.substring(start, end);
    }

    /** Quick map builder for SSE event data */
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
