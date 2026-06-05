package com.scripttool.repository;

import com.scripttool.model.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByUserIdOrderByNameAsc(Long userId);
    long countByUserId(Long userId);
}
