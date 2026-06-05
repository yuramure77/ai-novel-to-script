package com.scripttool.service;

import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.ScriptVersion;
import com.scripttool.repository.ProjectRepository;
import com.scripttool.repository.ScriptVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ScriptVersionRepository scriptVersionRepository;

    public ProjectService(ProjectRepository projectRepository,
                          ScriptVersionRepository scriptVersionRepository) {
        this.projectRepository = projectRepository;
        this.scriptVersionRepository = scriptVersionRepository;
    }

    public List<Project> listUserProjects(Long userId) {
        return projectRepository.findByUserIdOrderByUpdatedAtDesc(userId);
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
