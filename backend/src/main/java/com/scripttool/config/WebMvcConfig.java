package com.scripttool.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // SPA fallback — all non-API routes serve index.html
        registry.addViewController("/{spring:[^.]+}").setViewName("forward:/index.html");
        registry.addViewController("/projects/{spring:[^.]+}").setViewName("forward:/index.html");
        registry.addViewController("/project/{spring:[^.]+}").setViewName("forward:/index.html");
    }
}
