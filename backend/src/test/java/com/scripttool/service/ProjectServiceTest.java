package com.scripttool.service;

import com.scripttool.model.entity.Project;
import com.scripttool.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("项目服务 — CRUD+权限")
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepo;
    @Mock private ScriptVersionRepository svRepo;
    @Mock private CollaborationRepository collabRepo;
    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectRepo, svRepo, collabRepo);
    }

    @Test @DisplayName("创建项目成功")
    void createProject() {
        when(projectRepo.save(any())).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
        Project p = service.createProject(1L, "测试项目", "这是测试内容");
        assertEquals("测试项目", p.getTitle());
        assertEquals(Project.ProjectStatus.DRAFT, p.getStatus());
    }

    @Test @DisplayName("getProject查不到抛异常")
    void getNotFoundThrows() {
        when(projectRepo.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.getProject(999L));
    }

    @Test @DisplayName("getPermission: owner返回ADMIN")
    void ownerPermission() {
        Project p = new Project(1L, "test", "content");
        p.setId(10L);
        when(projectRepo.findById(10L)).thenReturn(Optional.of(p));
        assertEquals("ADMIN", service.getPermission(10L, 1L));
    }

    @Test @DisplayName("listUserProjects含分享项目")
    void listIncludesShared() {
        when(projectRepo.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(collabRepo.findByUserId(1L)).thenReturn(List.of());
        List<Project> projects = service.listUserProjects(1L);
        assertNotNull(projects);
    }
}
