package com.scripttool.repository;

import com.scripttool.model.entity.ImageVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageVersionRepository extends JpaRepository<ImageVersion, Long> {

    List<ImageVersion> findByProjectIdAndImageTypeAndTargetIndexOrderByCreatedAtDesc(
            Long projectId, ImageVersion.ImageType imageType, Integer targetIndex);

    List<ImageVersion> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
