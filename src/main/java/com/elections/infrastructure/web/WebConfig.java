package com.elections.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/** CORS para el frontend desplegado en otro dominio (Vercel). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] origins;

    public WebConfig(@Value("${app.cors-origins:*}") String origins) {
        this.origins = parseOrigins(origins);
    }

    public static String[] parseOrigins(String raw) {
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Retry-After")
                .maxAge(3600);
    }
}
