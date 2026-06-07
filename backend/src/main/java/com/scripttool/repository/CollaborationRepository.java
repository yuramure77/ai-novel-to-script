package com.scripttool.repository;

import com.scripttool.model.entity.Collaboration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollaborationRepository extends JpaRepository<Collaboration, Long> {

    List<Collaboration> findByProjectId(Long projectId);

    List<Collaboration> findByUserId(Long userId);

    Optional<Collaboration> findByProjectIdAndUserId(Long projectId, Long userId);

    void deleteByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);
}
