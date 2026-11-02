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
     * a pooled DB connection for the whole AI call, so ten concurrent chats starve every other
     * request. These endpoints only touch entities inside short repository transactions.
     */
    static final String[] RAG_WAITING_PATHS = {"/api/rag/chat", "/api/admin/quizzes/generate"};

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
                registry.addWebRequestInterceptor(interceptor).excludePathPatterns(RAG_WAITING_PATHS));
    }
}
