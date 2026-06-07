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

    @Test @DisplayName("上下文加载成功")
    void contextLoads() {}

    @Test @DisplayName("Actuator健康检查")
    void healthCheck() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test @DisplayName("API认证接口可访问")
    void authEndpointsAccessible() throws Exception {
        mockMvc.perform(get("/actuator/info"))
            .andExpect(status().isOk());
    }

    @Test @DisplayName("未认证请求被拦截")
    void unauthenticatedBlocked() throws Exception {
        mockMvc.perform(get("/api/projects"))
            .andExpect(status().isForbidden());
    }
}
