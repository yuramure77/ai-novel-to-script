package com.scripttool.repository;

import com.scripttool.model.entity.GenerationProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenerationProgressRepository extends JpaRepository<GenerationProgress, Long> {

    Optional<GenerationProgress> findByProjectId(Long projectId);

    void deleteByProjectId(Long projectId);
}
