package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoAccessServiceTest {
    @TempDir Path directory;
    LessonRepository lessons = mock(LessonRepository.class);
    UserRepository users = mock(UserRepository.class);
    Lesson lesson;
    User user;
    VideoAccessService service;
    @BeforeEach void setup() throws Exception {
        Topic topic = Topic.builder().id("topic").build();
        lesson = Lesson.builder().id("lesson").topic(topic).videoSource(VideoSource.UPLOAD)
                .videoUrl("/uploads/videos/video.mp4").build();
        user = User.builder().username("learner").role("ROLE_USER").registeredTopics(new HashSet<>(Set.of(topic))).build();
        when(lessons.findById("lesson")).thenReturn(Optional.of(lesson));
        when(users.findByUsername("learner")).thenReturn(Optional.of(user));
        Files.writeString(directory.resolve("video.mp4"), "video");
        service = service(3600);
    }
    VideoAccessService service(long duration) {
        return new VideoAccessService(lessons, users, directory.toString(), "123456789012345678901234567890123456", duration);
    }
    String token(VideoAccessService service) { return service.grantAccess("lesson", "learner").streamUrl().split("token=")[1]; }
    void forbidden(Runnable action) { assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, action::run).getStatusCode()); }
    @Test void allowsRegisteredLearnerAndProducesAuthorizedPath() { assertEquals(directory.resolve("video.mp4"), service.authorizeStreamPath(token(service))); }
    @Test void rejectsUnregisteredLearner() { user.getRegisteredTopics().clear(); forbidden(() -> service.grantAccess("lesson", "learner")); }
    @Test void revokesPreviouslyGrantedAccessWhenRegistrationRemoved() { String token = token(service); user.getRegisteredTopics().clear(); forbidden(() -> service.authorizeStream(token)); }
    @Test void rejectsTamperedToken() { String token = token(service); forbidden(() -> service.authorizeStream("x" + token)); }
    @Test void rejectsExpiredToken() { VideoAccessService expired = service(-60); String token = token(expired); forbidden(() -> service.authorizeStream(token)); }
    @Test void rejectsTokenAfterLessonVideoChanged() { String token = token(service); lesson.setVideoUrl("/uploads/videos/new.mp4"); forbidden(() -> service.authorizeStream(token)); }
    @Test void rejectsTraversal() { lesson.setVideoUrl("/uploads/videos/../secret.mp4"); assertThrows(ResponseStatusException.class, () -> service.grantAccess("lesson", "learner")); }
    @Test void allowsAdminWithoutRegistration() { user.getRegisteredTopics().clear(); user.setRole("ROLE_ADMIN"); assertEquals("video.mp4", service.authorizeStream(token(service))); }
}
