package com.scripttool.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class YamlGeneratorServiceTest {

    private final YamlGeneratorService service = new YamlGeneratorService();

    @Test
    void shouldGenerateBasicYaml() {
        List<Map<String, Object>> characters = List.of(
                Map.of("name", "李云", "role", "protagonist", "description", "剑客", "traits", List.of("勇敢")),
                Map.of("name", "赵铁山", "role", "antagonist", "description", "寨主", "traits", List.of("凶狠"))
        );

        List<Map<String, Object>> scenes = List.of(
                Map.of("chapter", 1, "scene_number", 1, "type", "EXT", "location", "长安",
                       "time", "黄昏", "description", "古道", "characters", List.of("李云"),
                       "beats", List.of(
                               Map.of("type", "action", "character", "null", "line", "null",
                                      "direction", "远景，古道", "emotion", "null"),
                               Map.of("type", "dialogue", "character", "李云", "line", "该来了",
                                      "direction", "低语", "emotion", "沉重")
                       ))
        );

        String yaml = service.generate("测试", "原著", "作者", characters, scenes);
        assertNotNull(yaml);
        assertTrue(yaml.contains("测试"), "Should contain title");
        assertTrue(yaml.contains("李云"), "Should contain character name");
        assertTrue(yaml.contains("EXT"), "Should contain scene type");
    }

    @Test
    void shouldHandleEmptyData() {
        String yaml = service.generate("空", "无", "无", List.of(), List.of());
        assertNotNull(yaml);
        assertTrue(yaml.contains("空"));
    }
}
