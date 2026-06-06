package com.scripttool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootApplication
public class ScriptToolApplication {

    static {
        SecurityContextHolder.setStrategyName(
                SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    public static void main(String[] args) {
        SpringApplication.run(ScriptToolApplication.class, args);
    }
}
