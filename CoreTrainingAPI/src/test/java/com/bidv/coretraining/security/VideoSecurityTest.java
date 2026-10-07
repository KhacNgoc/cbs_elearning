package com.bidv.coretraining.security;

import com.bidv.coretraining.controller.VideoController;
import com.bidv.coretraining.service.VideoAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VideoController.class, properties = {"app.cors.allowed-origins=http://localhost:5173", "server.port=0", "server.servlet.context-path=/", "spring.application.name=test", "spring.profiles.active=test", "spring.servlet.multipart.max-file-size=500MB", "spring.servlet.multipart.max-request-size=510MB"})
@Import(SecurityConfig.class)
class VideoSecurityTest {
    @MockBean com.bidv.coretraining.config.WebConfig webConfig;
    @Autowired MockMvc mvc;
    @MockBean VideoAccessService videoAccessService;
    @MockBean JwtTokenProvider tokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtCookieService jwtCookieService;
    @Test void deniesDirectVideoToAuthenticatedLearnerAndAdmin() throws Exception {
        mvc.perform(get("/uploads/videos/video.mp4").with(user("learner").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/uploads/videos/video.mp4").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void accessEndpointRequiresLogin() throws Exception { mvc.perform(get("/api/videos/access/lesson")).andExpect(status().isUnauthorized()); }
}
