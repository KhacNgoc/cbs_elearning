package com.bidv.coretraining.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    /**
     * Requests that wait synchronously on the RAG service. An open-in-view session would hold
     * a pooled DB connection for the whole AI call, so a few slow calls starve every other
     * request, including the CSRF and session checks the UI waits on. These endpoints only
     * touch entities inside short transactions.
     */
    static final String[] RAG_WAITING_PATHS = {"/api/rag/chat", "/api/admin/quizzes/generate",
            "/api/admin/quizzes/generate/jobs/**", "/api/admin/lessons/*/flashcards/generate/**",
            "/api/admin/lessons/*/ai-summarize-changes", "/api/admin/courses/*/documents/*/outline"};

    /**
     * File downloads: open-in-view would keep the connection used by the access check until the
     * last byte reaches a slow client, so a classroom opening one deck at once drains the pool.
     * The access check runs in its own read-only transaction.
     */
    static final String[] FILE_DOWNLOAD_PATHS = {"/uploads/documents/**"};

    private final ObjectProvider<OpenEntityManagerInViewInterceptor> openInView;

    public WebConfig(ObjectProvider<OpenEntityManagerInViewInterceptor> openInView) {
        this.openInView = openInView;
    }

    /**
     * Replaces Spring Boot's open-in-view interceptor, which backs off when this bean exists,
     * under the same {@code spring.jpa.open-in-view} switch.
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.jpa", name = "open-in-view", havingValue = "true",
            matchIfMissing = true)
    public OpenEntityManagerInViewInterceptor openEntityManagerInViewInterceptor() {
        return new OpenEntityManagerInViewInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        openInView.ifAvailable(interceptor ->
                registry.addWebRequestInterceptor(interceptor).excludePathPatterns(RAG_WAITING_PATHS)
                        .excludePathPatterns(FILE_DOWNLOAD_PATHS));
    }
}
