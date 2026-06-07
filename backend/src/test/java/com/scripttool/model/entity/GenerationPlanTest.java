package com.scripttool.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GenerationPlan — 任务制断点续传")
class GenerationPlanTest {

    private GenerationPlan createPlan(int totalChapters) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 0; i < totalChapters; i++) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("num", i + 1); t.put("title", "第" + (i+1) + "章");
            t.put("status", "PENDING"); t.put("sceneCount", 0); t.put("charCount", 0);
            tasks.add(t);
        }
        return new GenerationPlan(1L, tasks, 1);
    }

    @Test
    @DisplayName("新plan所有章节PENDING")
    void freshPlanAllPending() {
        GenerationPlan plan = createPlan(5);
        assertEquals(5, plan.getTotalChapters());
        assertEquals(0, plan.getCompletedChapters());
        assertEquals(0, plan.firstPendingChapter());
        assertFalse(plan.isComplete());
    }

    @Test
    @DisplayName("status流转: PENDING→IN_PROGRESS→DONE")
    void statusTransition() {
        GenerationPlan plan = createPlan(3);
        plan.markInProgress(0);
        assertEquals("IN_PROGRESS", plan.getChapterTasks().get(0).get("status"));
        plan.markDone(0, 4, 3);
        assertEquals("DONE", plan.getChapterTasks().get(0).get("status"));
        assertEquals(1, plan.getCompletedChapters());
        assertEquals(1, plan.firstPendingChapter());
    }

    @Test
    @DisplayName("完成所有章节后isComplete=true")
    void allDoneIsComplete() {
        GenerationPlan plan = createPlan(3);
        for (int i = 0; i < 3; i++) plan.markDone(i, 2, 2);
        assertTrue(plan.isComplete());
        assertEquals(-1, plan.firstPendingChapter());
        assertEquals(3, plan.getCompletedChapters());
    }

    @Test
    @DisplayName("mergeCharacters同名去重")
    void mergeCharactersDedup() {
        GenerationPlan plan = createPlan(3);
        plan.mergeCharacters(List.of(
            Map.of("name", "清显", "role", "protagonist"),
            Map.of("name", "本多", "role", "supporting")
        ));
        assertEquals(2, plan.getAccumulatedCharacters().size());
        plan.mergeCharacters(List.of(Map.of("name", "清显", "role", "protagonist")));
        assertEquals(2, plan.getAccumulatedCharacters().size(), "同名角色不应重复");
    }

    @Test
    @DisplayName("appendScenes自动编号")
    void appendScenesNumbering() {
        GenerationPlan plan = createPlan(3);
        plan.appendScenes(List.of(
            new LinkedHashMap<>(Map.of("description", "A")),
            new LinkedHashMap<>(Map.of("description", "B"))
        ));
        assertEquals(1, plan.getAccumulatedScenes().get(0).get("scene_number"));
        assertEquals(2, plan.getAccumulatedScenes().get(1).get("scene_number"));
    }

    @Test
    @DisplayName("toPlanData包含完整进度")
    void planDataFields() {
        GenerationPlan plan = createPlan(5);
        plan.markDone(0, 3, 2);
        Map<String, Object> data = plan.toPlanData();
        assertEquals(5, data.get("totalChapters"));
        assertEquals(1, data.get("completedChapters"));
    }
}
