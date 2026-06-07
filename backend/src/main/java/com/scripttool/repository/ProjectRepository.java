package com.scripttool.repository;

import com.scripttool.model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByUserIdOrderByUpdatedAtDesc(Long userId);
    long countByUserId(Long userId);
    Optional<Project> findByInviteToken(String inviteToken);
}
