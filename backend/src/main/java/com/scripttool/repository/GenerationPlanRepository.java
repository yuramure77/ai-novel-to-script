package com.scripttool.repository;

import com.scripttool.model.entity.GenerationPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenerationPlanRepository extends JpaRepository<GenerationPlan, Long> {

    Optional<GenerationPlan> findByProjectId(Long projectId);

    void deleteByProjectId(Long projectId);
}
