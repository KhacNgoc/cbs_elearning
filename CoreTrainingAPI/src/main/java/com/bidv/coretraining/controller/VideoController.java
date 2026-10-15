package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.VideoAccessDto;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.service.VideoAccessService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    public ResponseEntity<?> stream(
            @RequestParam String token,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Accel-Supported", required = false) String accelSupported)
            throws IOException {
        Path video = videoAccessService.authorizeStreamPath(token);
        if ("true".equalsIgnoreCase(accelSupported)) {
            return ResponseEntity.ok()
                    .header("X-Accel-Redirect", "/_protected_videos/" + video.getFileName())
                    .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                    .build();
        }

        Resource resource = new FileSystemResource(video);
        String detectedType = Files.probeContentType(video);
        MediaType mediaType = detectedType == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(detectedType);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(resource.contentLength())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .body(resource);
    }
}
