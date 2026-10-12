package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.VideoAccessDto;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.VideoSource;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;

@Service
public class VideoAccessService {

    private static final String UPLOAD_PREFIX = "/uploads/videos/";

    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final Path videoDirectory;
    private final SecretKey signingKey;
    private final long accessDurationSeconds;

    public VideoAccessService(
            LessonRepository lessonRepository,
            UserRepository userRepository,
            @Value("${app.storage.videos-directory:uploads/videos}") String videoDirectory,
            @Value("${app.video-access.secret:${app.jwt.secret}}") String secret,
            @Value("${app.video-access.duration-seconds:3600}") long accessDurationSeconds) {
        this.lessonRepository = lessonRepository;
        this.userRepository = userRepository;
        this.videoDirectory = Path.of(videoDirectory).toAbsolutePath().normalize();
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessDurationSeconds = accessDurationSeconds;
    }

    @Transactional(readOnly = true)
    public VideoAccessDto grantAccess(String lessonId, String username) {
        Lesson lesson = findLocalVideoLesson(lessonId);
        assertCanWatch(lesson, username);
        String fileName = extractSafeFileName(lesson.getVideoUrl());
        Path video = videoDirectory.resolve(fileName).normalize();
        if (!video.startsWith(videoDirectory) || !Files.isRegularFile(video)) {
            throw new ResourceNotFoundException("Video", "lessonId", lessonId);
        }

        Instant expiresAt = Instant.now().plusSeconds(accessDurationSeconds);
        String token = Jwts.builder()
                .setSubject(userRepository.findByUsername(username).orElseThrow().getId().toString())
                .claim("ver", userRepository.findByUsername(username).orElseThrow().getTokenVersion())
                .claim("lessonId", lessonId)
                .claim("fileName", fileName)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new VideoAccessDto("/api/videos/stream?token=" + token, expiresAt.toEpochMilli());
    }

    private void assertCanWatch(Lesson lesson, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        LearningPolicy.assertLessonAccess(user,lesson);
    }

    @Transactional(readOnly = true)
    public String authorizeStream(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            String lessonId = claims.get("lessonId", String.class);
            String fileName = claims.get("fileName", String.class);
            Lesson lesson = findLocalVideoLesson(lessonId);
            User viewer=userRepository.findById(Long.valueOf(claims.getSubject())).orElseThrow();
            assertCanWatch(lesson, viewer.getUsername());
            Number ver=claims.get("ver",Number.class);
            com.bidv.coretraining.security.AccessPolicy.require(ver!=null && ver.longValue()==viewer.getTokenVersion());
            String currentFileName = extractSafeFileName(lesson.getVideoUrl());
            if (!currentFileName.equals(fileName)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quyền xem video không còn hợp lệ");
            }
            return fileName;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quyền xem video không hợp lệ hoặc đã hết hạn");
        }
    }

    public Path authorizeStreamPath(String token) {
        String fileName = authorizeStream(token);
        Path video = videoDirectory.resolve(fileName).normalize();
        if (!video.startsWith(videoDirectory) || !Files.isRegularFile(video)) {
            throw new ResourceNotFoundException("Video", "fileName", fileName);
        }
        return video;
    }

    private Lesson findLocalVideoLesson(String lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        if (lesson.getVideoSource() != VideoSource.UPLOAD
                && (lesson.getVideoUrl() == null || !lesson.getVideoUrl().startsWith(UPLOAD_PREFIX))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bài học không sử dụng video lưu trên server");
        }
        return lesson;
    }

    private String extractSafeFileName(String videoUrl) {
        if (videoUrl == null || !videoUrl.startsWith(UPLOAD_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn video không hợp lệ");
        }
        String fileName = videoUrl.substring(UPLOAD_PREFIX.length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn video không hợp lệ");
        }
        return fileName;
    }
}
