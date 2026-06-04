package com.scripttool.repository;

import com.scripttool.model.entity.ScriptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScriptVersionRepository extends JpaRepository<ScriptVersion, Long> {
    List<ScriptVersion> findByProjectIdOrderByVersionNumberDesc(Long projectId);
    Optional<ScriptVersion> findTopByProjectIdOrderByVersionNumberDesc(Long projectId);
    long countByProjectId(Long projectId);
}
