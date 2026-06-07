package com.scripttool.model.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task-based generation plan — replaces the fragile bitmap checkpoint.
 * Each chapter is a task with PENDING/IN_PROGRESS/DONE status.
 * On interrupt, reload from DB and resume from first PENDING chapter.
 */
@Entity
@Table(name = "generation_plan")
public class GenerationPlan {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    @Column(nullable = false)
    private int totalChapters;

    @Column(nullable = false)
    private int completedChapters;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** JSON array of chapter tasks: [{num, title, status, sceneCount, charCount}, ...] */
    @Column(name = "chapters_json", columnDefinition = "CLOB")
    private String chaptersJson;

    /** Accumulated final YAML from completed chapters */
    @Column(name = "partial_yaml", columnDefinition = "CLOB")
    private String partialYaml;

    /** JSON array of accumulated characters: [{name, role, description, traits}, ...] */
    @Column(name = "characters_json", columnDefinition = "CLOB")
    private String charactersJson;

    /** JSON array of accumulated scenes: [{chapter, scene_number, ...}, ...] */
    @Column(name = "scenes_json", columnDefinition = "CLOB")
    private String scenesJson;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public GenerationPlan() {}

    /** Create a fresh plan from chapter split results */
    public GenerationPlan(Long projectId, List<Map<String, Object>> chapterTasks,
                          int versionNumber) {
        this.projectId = projectId;
        this.totalChapters = chapterTasks.size();
        this.completedChapters = 0;
        this.versionNumber = versionNumber;
        this.chaptersJson = toJson(chapterTasks);
        this.partialYaml = "";
        this.charactersJson = "[]";
        this.scenesJson = "[]";
    }

    // ── Chapter task helpers ──

    public List<Map<String, Object>> getChapterTasks() {
        try {
            return mapper.readValue(chaptersJson, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void setChapterTasks(List<Map<String, Object>> tasks) {
        this.chaptersJson = toJson(tasks);
    }

    /** Mark a chapter as IN_PROGRESS and persist */
    public void markInProgress(int chapterIdx) {
        List<Map<String, Object>> tasks = getChapterTasks();
        if (chapterIdx >= 0 && chapterIdx < tasks.size()) {
            tasks.get(chapterIdx).put("status", "IN_PROGRESS");
            setChapterTasks(tasks);
        }
    }

    /** Mark a chapter as DONE, record scene/char counts, update completedChapters */
    public void markDone(int chapterIdx, int newScenes, int newChars) {
        List<Map<String, Object>> tasks = getChapterTasks();
        if (chapterIdx >= 0 && chapterIdx < tasks.size()) {
            Map<String, Object> t = tasks.get(chapterIdx);
            t.put("status", "DONE");
            t.put("sceneCount", newScenes);
            t.put("charCount", newChars);
            setChapterTasks(tasks);
        }
        this.completedChapters = (int) tasks.stream()
            .filter(t -> "DONE".equals(t.get("status"))).count();
    }

    /** Find first PENDING chapter index, or -1 if all done */
    public int firstPendingChapter() {
        List<Map<String, Object>> tasks = getChapterTasks();
        for (int i = 0; i < tasks.size(); i++) {
            if (!"DONE".equals(tasks.get(i).get("status"))) return i;
        }
        return -1;
    }

    public boolean isComplete() {
        return firstPendingChapter() == -1;
    }

    // ── Characters accumulator ──

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAccumulatedCharacters() {
        try {
            return mapper.readValue(charactersJson, new TypeReference<>() {});
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public void mergeCharacters(List<Map<String, Object>> newChars) {
        List<Map<String, Object>> existing = getAccumulatedCharacters();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (var c : existing) names.add(String.valueOf(c.getOrDefault("name", "")));
        for (var c : newChars) {
            String name = String.valueOf(c.getOrDefault("name", ""));
            if (!name.isBlank() && names.add(name)) existing.add(c);
        }
        this.charactersJson = toJson(existing);
    }

    // ── Scenes accumulator ──

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAccumulatedScenes() {
        try {
            return mapper.readValue(scenesJson, new TypeReference<>() {});
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public void appendScenes(List<Map<String, Object>> newScenes) {
        List<Map<String, Object>> existing = getAccumulatedScenes();
        existing.addAll(newScenes);
        // Re-number scenes sequentially
        for (int i = 0; i < existing.size(); i++) {
            existing.get(i).put("scene_number", i + 1);
        }
        this.scenesJson = toJson(existing);
    }

    // ── Data for SSE ──

    /** Build the plan data sent to frontend */
    public Map<String, Object> toPlanData() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalChapters", totalChapters);
        m.put("completedChapters", completedChapters);
        m.put("versionNumber", versionNumber);
        m.put("chapters", getChapterTasks());
        m.put("isComplete", isComplete());
        return m;
    }

    // ── JSON util ──

    private static String toJson(Object obj) {
        try { return mapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "[]"; }
    }

    // ── Getters/Setters ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public int getTotalChapters() { return totalChapters; }
    public void setTotalChapters(int totalChapters) { this.totalChapters = totalChapters; }
    public int getCompletedChapters() { return completedChapters; }
    public void setCompletedChapters(int completedChapters) { this.completedChapters = completedChapters; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getChaptersJson() { return chaptersJson; }
    public void setChaptersJson(String chaptersJson) { this.chaptersJson = chaptersJson; }
    public String getPartialYaml() { return partialYaml; }
    public void setPartialYaml(String partialYaml) { this.partialYaml = partialYaml; }
    public String getCharactersJson() { return charactersJson; }
    public void setCharactersJson(String charactersJson) { this.charactersJson = charactersJson; }
    public String getScenesJson() { return scenesJson; }
    public void setScenesJson(String scenesJson) { this.scenesJson = scenesJson; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
