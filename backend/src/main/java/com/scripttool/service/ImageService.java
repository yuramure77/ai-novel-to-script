package com.scripttool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Free image generation via Pollinations.ai (no API key needed)
 */
@Service
public class ImageService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ImageService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a scene visualization image.
     * Returns a URL to the generated image.
     */
    public String generateSceneImage(String sceneDescription, String location, String time, String mood) {
        String prompt = buildPrompt(sceneDescription, location, time, mood);
        // Pollinations.ai URL format
        String encoded = prompt.replace(" ", "%20").replace("\n", "%20");
        return "https://image.pollinations.ai/prompt/" + encoded + "?width=1024&height=576&nologo=true";
    }

    /**
     * Generate character portrait
     */
    public String generateCharacterImage(String name, String description, List<String> traits) {
        String prompt = String.format(
            "character portrait of %s, %s, traits: %s, cinematic lighting, detailed, professional photography style",
            name,
            description != null ? description : "Chinese historical figure",
            traits != null ? String.join(", ", traits) : "mysterious"
        );
        String encoded = prompt.replace(" ", "%20");
        return "https://image.pollinations.ai/prompt/" + encoded + "?width=768&height=768&nologo=true";
    }

    private String buildPrompt(String sceneDesc, String location, String time, String mood) {
        return String.format(
            "cinematic scene: %s, location: %s, time: %s, mood: %s, Chinese historical drama style, movie still, cinematic lighting, 8K, highly detailed",
            sceneDesc != null ? sceneDesc : "dramatic scene",
            location != null ? location : "ancient China",
            time != null ? time : "dusk",
            mood != null ? mood : "dramatic"
        );
    }
}
