package com.bidv.coretraining.config;

import com.bidv.coretraining.service.DocumentStorageService;
import com.bidv.coretraining.service.VideoStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final DocumentStorageService documentStorageService;
    private final VideoStorageService videoStorageService;

    public WebConfig(DocumentStorageService documentStorageService, VideoStorageService videoStorageService) {
        this.documentStorageService = documentStorageService;
        this.videoStorageService = videoStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String storageLocation = documentStorageService.getStorageDirectory().toUri().toString();
        registry.addResourceHandler("/uploads/documents/**")
                .addResourceLocations(storageLocation);
        registry.addResourceHandler("/uploads/videos/**")
                .addResourceLocations(videoStorageService.getStorageDirectory().toUri().toString());
    }
}
