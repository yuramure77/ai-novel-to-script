package com.scripttool.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Checkpoint for incremental script generation.
 * Tracks which 1000-char chunks have been processed.
 * When all chunks are done, the generation is complete.
 */
@Entity
@Table(name = "generation_progress")
public class GenerationProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    @Column(nullable = false)
    private int totalChunks;

    /** Bitmap: '1' = done, '0' = pending. Index = chunk number. */
    @Column(nullable = false, length = 10000)
    private String chunkMap;

    /** Accumulated YAML from completed chunks */
    @Column(columnDefinition = "CLOB")
    private String partialYaml;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public GenerationProgress() {}

    public GenerationProgress(Long projectId, int totalChunks, int versionNumber) {
        this.projectId = projectId;
        this.totalChunks = totalChunks;
        this.chunkMap = "0".repeat(totalChunks);
        this.partialYaml = "";
        this.versionNumber = versionNumber;
    }

    /** Mark chunk as completed */
    public void markDone(int chunkIndex) {
        if (chunkIndex < 0 || chunkIndex >= totalChunks) return;
        char[] chars = chunkMap.toCharArray();
        chars[chunkIndex] = '1';
        this.chunkMap = new String(chars);
    }

    /** Mark range of chunks [from, to] as completed */
    public void markRangeDone(int fromChunk, int toChunk) {
        char[] chars = chunkMap.toCharArray();
        for (int i = Math.max(0, fromChunk); i <= Math.min(toChunk, totalChunks - 1); i++) {
            chars[i] = '1';
        }
        this.chunkMap = new String(chars);
    }

    /** Get the first incomplete chunk index, or -1 if all done */
    public int firstIncompleteChunk() {
        int idx = chunkMap.indexOf('0');
        return idx >= 0 ? idx : -1;
    }

    /** Check if all chunks are done */
    public boolean isComplete() {
        return firstIncompleteChunk() == -1;
    }

    /** How many chunks are completed */
    public int completedCount() {
        int count = 0;
        for (char c : chunkMap.toCharArray()) {
            if (c == '1') count++;
        }
        return count;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    public String getChunkMap() { return chunkMap; }
    public void setChunkMap(String chunkMap) { this.chunkMap = chunkMap; }
    public String getPartialYaml() { return partialYaml; }
    public void setPartialYaml(String partialYaml) { this.partialYaml = partialYaml; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
