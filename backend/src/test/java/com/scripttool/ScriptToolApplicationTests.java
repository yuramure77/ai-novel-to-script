package com.scripttool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("应用集成测试")
class ScriptToolApplicationTests {

    @Autowired private MockMvc mockMvc;

    @Test @DisplayName("Spring上下文加载")
    void contextLoads() {}

    @Test @DisplayName("Actuator健康检查")
    void healthCheck() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test @DisplayName("API认证拦截")
    void unauthenticatedBlocked() throws Exception {
        mockMvc.perform(get("/api/projects"))
            .andExpect(status().isForbidden());
    }

    @Test @DisplayName("登录接口可访问")
    void authEndpointAccessible() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
            .andExpect(status().is(405)); // POST only, but endpoint exists
    }
}
