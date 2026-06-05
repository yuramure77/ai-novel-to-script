package com.scripttool.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExportServiceTest {

    private final ExportService service = new ExportService();

    @Test
    void shouldConvertToMarkdown() {
        String yaml = """
script:
  title: "测试剧本"
  author: "张三"
  based_on: "原著"
characters:
  - name: "主角"
    role: protagonist
    description: "一个英雄"
    traits:
      - "勇敢"
scenes:
  - id: SCENE_001
    chapter: 1
    scene_number: 1
    type: EXT
    location: "广场"
    time: "清晨"
    description: "雾蒙蒙"
    characters:
      - "主角"
    beats:
      - type: action
        character: null
        line: null
        direction: "镜头推进"
        emotion: null
      - type: dialogue
        character: "主角"
        line: "你好"
        direction: "挥手"
        emotion: "开心"
""";

        String md = service.yamlToMarkdown(yaml);
        assertNotNull(md);
        assertTrue(md.contains("测试剧本"));
        assertTrue(md.contains("主角"));
        assertTrue(md.contains("你好"));
    }

    @Test
    void shouldConvertToFountain() {
        String yaml = """
script:
  title: "测试"
characters:
  - name: "A"
    role: protagonist
scenes:
  - id: SCENE_001
    chapter: 1
    scene_number: 1
    type: INT
    location: "房间"
    time: "夜晚"
    beats:
      - type: dialogue
        character: "A"
        line: "测试台词"
        direction: null
        emotion: "平静"
""";

        String fountain = service.yamlToFountain(yaml);
        assertNotNull(fountain);
        assertTrue(fountain.contains("测试"), "Should contain title");
        assertTrue(fountain.contains("A"), "Should contain character");
        assertTrue(fountain.contains("测试台词"), "Should contain dialog");
    }
}
