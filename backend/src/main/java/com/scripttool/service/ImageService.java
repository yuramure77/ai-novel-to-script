package com.scripttool.service;

import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ImageService {

    public String generateSceneImage(String desc, String location, String time, String mood) {
        String prompt = String.format(
            "cinematic scene, %s, location %s, time %s, mood %s, Chinese historical drama style, movie still, cinematic lighting, highly detailed",
            desc != null ? desc : "dramatic scene",
            location != null ? location : "ancient China",
            time != null ? time : "dusk",
            mood != null ? mood : "dramatic"
        );
        return "https://image.pollinations.ai/prompt/" + encode(prompt) + "?width=1024&height=576&nologo=true";
    }

    public String generateCharacterImage(String name, String description, List<String> traits) {
        String prompt = String.format(
            "character portrait of %s, %s, traits: %s, cinematic lighting, professional photography style",
            name != null ? name : "character",
            description != null ? description : "historical figure",
            traits != null ? String.join(", ", traits) : "mysterious"
        );
        return "https://image.pollinations.ai/prompt/" + encode(prompt) + "?width=768&height=768&nologo=true";
    }

    private String encode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s.replace(" ", "%20");
        }
    }
}
