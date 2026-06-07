package com.scripttool.service;

import com.scripttool.model.entity.Collaboration;
import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.repository.CollaborationRepository;
import com.scripttool.repository.ProjectRepository;
import com.scripttool.repository.ScriptVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ScriptVersionRepository scriptVersionRepository;
    private final CollaborationRepository collabRepository;

    public ProjectService(ProjectRepository projectRepository,
                          ScriptVersionRepository scriptVersionRepository,
                          CollaborationRepository collabRepository) {
        this.projectRepository = projectRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.collabRepository = collabRepository;
    }

    public List<Project> listUserProjects(Long userId) {
        List<Project> own = projectRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        // Also include projects shared with this user
        List<Collaboration> shared = collabRepository.findByUserId(userId);
        Set<Long> ownIds = new HashSet<>();
        for (Project p : own) ownIds.add(p.getId());

        List<Project> all = new ArrayList<>(own);
        for (Collaboration c : shared) {
            if (!ownIds.contains(c.getProjectId())) {
                projectRepository.findById(c.getProjectId()).ifPresent(all::add);
            }
        }
        return all;
    }

    /** Get the user's permission for a project (null if no access) */
    public String getPermission(Long projectId, Long userId) {
        Project project = getProject(projectId);
        if (project.getUserId().equals(userId)) return "ADMIN";
        return collabRepository.findByProjectIdAndUserId(projectId, userId)
                .map(c -> c.getPermission().name())
                .orElse(null);
    }

    public Project getProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("项目不存在"));
    }

    @Transactional
    public Project createProject(Long userId, String title, String originalText) {
        return projectRepository.save(new Project(userId, title, originalText));
    }

    @Transactional
    public Project updateProject(Project project) {
        return projectRepository.save(project);
    }

    @Transactional
    public Project updateOriginalText(Long id, String originalText) {
        Project project = getProject(id);
        project.setOriginalText(originalText);
        project.setStatus(Project.ProjectStatus.DRAFT);
        return projectRepository.save(project);
    }

    @Transactional
    public Project updateChapterCount(Long id, int count) {
        Project project = getProject(id);
        project.setChapterCount(count);
        return projectRepository.save(project);
    }

    @Transactional
    public Project updateStatus(Long id, Project.ProjectStatus status) {
        Project project = getProject(id);
        project.setStatus(status);
        return projectRepository.save(project);
    }

    @Transactional
    public void deleteProject(Long id) {
        scriptVersionRepository.findByProjectIdOrderByVersionNumberDesc(id)
                .forEach(v -> scriptVersionRepository.deleteById(v.getId()));
        projectRepository.deleteById(id);
    }

    public List<ScriptVersion> listScriptVersions(Long projectId) {
        return scriptVersionRepository.findByProjectIdOrderByVersionNumberDesc(projectId);
    }

    public ScriptVersion getLatestScriptVersion(Long projectId) {
        return scriptVersionRepository.findTopByProjectIdOrderByVersionNumberDesc(projectId).orElse(null);
    }

    @Transactional
    public ScriptVersion saveScriptVersion(Long projectId, String yamlContent) {
        int versionNumber = (int) (scriptVersionRepository.countByProjectId(projectId) + 1);
        return scriptVersionRepository.save(new ScriptVersion(projectId, versionNumber, yamlContent));
    }
}
