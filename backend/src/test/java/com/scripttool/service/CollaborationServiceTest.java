package com.scripttool.service;

import com.scripttool.model.entity.Collaboration;
import com.scripttool.model.entity.Project;
import com.scripttool.model.entity.User;
import com.scripttool.repository.CollaborationRepository;
import com.scripttool.repository.ProjectRepository;
import com.scripttool.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("协作服务 — 权限三层防护")
class CollaborationServiceTest {

    @Mock private CollaborationRepository collabRepo;
    @Mock private ProjectRepository projectRepo;
    @Mock private UserRepository userRepo;
    private CollaborationService service;

    private final Long ownerId = 1L, adminId = 2L, readerId = 3L, projectId = 100L;

    @BeforeEach
    void setUp() {
        service = new CollaborationService(collabRepo, projectRepo, userRepo);

        Project project = new Project();
        project.setId(projectId); project.setUserId(ownerId);
        lenient().when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        lenient().when(collabRepo.findByProjectIdAndUserId(projectId, adminId))
            .thenReturn(Optional.of(new Collaboration(projectId, adminId, Collaboration.Permission.ADMIN)));
        lenient().when(collabRepo.findByProjectIdAndUserId(projectId, readerId))
            .thenReturn(Optional.of(new Collaboration(projectId, readerId, Collaboration.Permission.READ)));
    }

    @Test @DisplayName("Owner=管理员")
    void ownerIsAdmin() {
        assertEquals(Collaboration.Permission.ADMIN, service.getEffectivePermission(projectId, ownerId));
        assertTrue(service.canEdit(projectId, ownerId));
    }

    @Test @DisplayName("管理员协作者可编辑")
    void adminCanEdit() {
        assertTrue(service.canEdit(projectId, adminId));
        assertTrue(service.canView(projectId, adminId));
    }

    @Test @DisplayName("只读协作者仅可查看")
    void readerCanViewOnly() {
        assertTrue(service.canView(projectId, readerId));
        assertFalse(service.canEdit(projectId, readerId));
    }

    @Test @DisplayName("无关用户零权限")
    void strangerNoAccess() {
        assertNull(service.getEffectivePermission(projectId, 999L));
        assertFalse(service.canView(projectId, 999L));
        assertFalse(service.canEdit(projectId, 999L));
    }

    @Test @DisplayName("邀请链接加入默认只读")
    void joinByTokenDefaultRead() {
        Project p = new Project();
        p.setId(projectId); p.setUserId(ownerId); p.setInviteToken("tok-123");
        when(projectRepo.findByInviteToken("tok-123")).thenReturn(Optional.of(p));
        when(collabRepo.existsByProjectIdAndUserId(projectId, 10L)).thenReturn(false);
        when(collabRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.joinByToken("tok-123", 10L);
        assertEquals(projectId, result.get("projectId"));
        assertEquals("READ", result.get("permission"));
    }
}
