package com.bidv.coretraining.controller;

import com.bidv.coretraining.service.VideoAccessService;
import com.bidv.coretraining.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VideoControllerTest {
    @TempDir Path directory;
    MockMvc mvc;
    VideoAccessService service;
    @BeforeEach void setup() throws Exception {
        Path video = Files.writeString(directory.resolve("video.mp4"), "0123456789");
        service = mock(VideoAccessService.class);
        when(service.authorizeStreamPath("valid")).thenReturn(video);
        mvc = MockMvcBuilders.standaloneSetup(new VideoController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void expiredStreamReturns403WithActionableMessage() throws Exception {
        when(service.authorizeStreamPath("expired")).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Quyền xem đã hết hạn"));
        mvc.perform(get("/api/videos/stream").param("token", "expired"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Quyền xem đã hết hạn"));
    }
    @Test void servesRangeForDirectBackendPlayback() throws Exception {
        mvc.perform(get("/api/videos/stream").param("token", "valid").header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().string("2345"));
    }
    @Test void delegatesAuthorizedFileToNginx() throws Exception {
        mvc.perform(get("/api/videos/stream").param("token", "valid").header("X-Accel-Supported", "true"))
                .andExpect(status().isOk()).andExpect(header().string("X-Accel-Redirect", "/_protected_videos/video.mp4"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().string(""));
    }
    @Test void rejectsOutOfBoundsRange() throws Exception {
        mvc.perform(get("/api/videos/stream").param("token", "valid").header("Range", "bytes=99-100"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }
}
