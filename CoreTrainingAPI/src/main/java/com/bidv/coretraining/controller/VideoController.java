package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.VideoAccessDto;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.service.VideoAccessService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoAccessService videoAccessService;

    public VideoController(VideoAccessService videoAccessService) {
        this.videoAccessService = videoAccessService;
    }

    @GetMapping("/access/{lessonId}")
    public ResponseEntity<VideoAccessDto> requestAccess(
            @PathVariable String lessonId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(videoAccessService.grantAccess(lessonId, userDetails.getUsername()));
    }

    @GetMapping("/stream")
    public ResponseEntity<Void> stream(@RequestParam String token) {
        String fileName = videoAccessService.authorizeStream(token);
        return ResponseEntity.ok()
                .header("X-Accel-Redirect", "/_protected_videos/" + fileName)
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .build();
    }
}
