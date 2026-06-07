package com.scripttool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripttool.config.DeepSeekConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScriptGenService — AI调用")
class ScriptGenServiceTest {

    @Mock private DeepSeekConfig config;
    @Mock private RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();
    private ScriptGenService service;

    @BeforeEach
    void setUp() {
        service = new ScriptGenService(config, restTemplate, mapper);
        lenient().when(config.getModel()).thenReturn("deepseek-chat");
        lenient().when(config.getMaxTokens()).thenReturn(4096);
        lenient().when(config.getTemperature()).thenReturn(0.7);
        lenient().when(config.getApiUrl()).thenReturn("https://api.deepseek.com/v1/chat/completions");
        lenient().when(config.createHeaders()).thenReturn(new org.springframework.http.HttpHeaders());
    }

    @Test @DisplayName("超大文本抛异常")
    void tooLongTextThrows() {
        String huge = "x".repeat(1_000_001);
        assertThrows(RuntimeException.class,
            () -> service.generate(huge, List.of(), null, null));
    }

    @Test @DisplayName("空章节返回空结果")
    void emptyChaptersReturnsEmpty() {
        ScriptGenService.ScriptResult r = service.generate("text", List.of(), null, null);
        assertNotNull(r);
        assertTrue(r.characters().isEmpty());
        assertTrue(r.scenes().isEmpty());
    }
}
