package com.scripttool.service;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class YamlGeneratorService {

    private final Yaml yaml;

    public YamlGeneratorService() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setAllowReadOnlyProperties(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        this.yaml = new Yaml(options);
    }

    public String generate(String title, String basedOn, String author,
                           List<Map<String, Object>> characters,
                           List<Map<String, Object>> scenes) {

        Map<String, Object> root = new LinkedHashMap<>();

        // Script metadata
        Map<String, Object> scriptMeta = new LinkedHashMap<>();
        scriptMeta.put("title", title);
        scriptMeta.put("based_on", basedOn);
        scriptMeta.put("author", author);
        scriptMeta.put("version", "1.0");
        scriptMeta.put("language", "zh-CN");
        scriptMeta.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        root.put("script", scriptMeta);

        // Characters
        root.put("characters", characters);

        // Scenes
        root.put("scenes", scenes);

        return yaml.dumpAsMap(root);
    }
}
