package com.bidv.coretraining.config;

import com.bidv.coretraining.service.DocumentStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final DocumentStorageService documentStorageService;

    public WebConfig(DocumentStorageService documentStorageService) {
        this.documentStorageService = documentStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String storageLocation = documentStorageService.getStorageDirectory().toUri().toString();
        registry.addResourceHandler("/uploads/documents/**")
                .addResourceLocations(storageLocation);
    }
}
