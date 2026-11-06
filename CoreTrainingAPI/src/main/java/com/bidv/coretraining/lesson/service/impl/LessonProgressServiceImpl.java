package com.bidv.coretraining.lesson.service.impl;

import com.bidv.coretraining.document.repository.LessonDocumentRepository;
import com.bidv.coretraining.enums.DocumentPurpose;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.lesson.repository.projection.LessonAccessView;
import com.bidv.coretraining.policy.LearningPolicy;
import com.bidv.coretraining.policy.LessonContentPolicy;


import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.learner.service.LearningAccessService;
import com.bidv.coretraining.lesson.dto.LessonProgressDto;
import com.bidv.coretraining.lesson.dto.LessonProgressRequest;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.entity.LessonProgress;
import com.bidv.coretraining.lesson.repository.LessonProgressRepository;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.lesson.service.LessonProgressService;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LessonProgressServiceImpl implements LessonProgressService {
    private final LearningAccessService access;
    private final LessonDocumentRepository documents;
    private final com.bidv.coretraining.video.service.VideoDurationService durations;
    private static final double MAX_PLAYBACK_RATE = 2;
    private final double watchGraceSeconds;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;

    public LessonProgressServiceImpl(UserRepository userRepository,
            LessonRepository lessonRepository,
            LessonProgressRepository progressRepository,
            LearningAccessService access, LessonDocumentRepository documents,
            com.bidv.coretraining.video.service.VideoDurationService durations,
            @org.springframework.beans.factory.annotation.Value("${app.progress.watch-grace-seconds:30}") double watchGraceSeconds) {
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.access = access;
        this.documents = documents;
        this.durations = durations;
        this.watchGraceSeconds = watchGraceSeconds;
    }

    @Transactional
    public LessonProgressDto saveProgress(String username, String lessonId, LessonProgressRequest request) {
        var authorized = access.requireLesson(username, lessonId);
        User user = authorized.user();
        userRepository.lockForLearning(user.getId()).orElseThrow(() -> new ResourceNotFoundException("User"));
        Lesson lesson = lessonRepository.getReferenceById(lessonId);
        LessonProgress progress = progressRepository.findByUserIdAndLessonId(user.getId(), lessonId)
                .orElseGet(() -> LessonProgress.builder().user(user).lesson(lesson).build());
        var before = toDto(progress);
        boolean lessonCompleted = userRepository.hasCompletedLesson(user.getId(), lessonId);
        boolean versionChanged = !java.util.Objects.equals(progress.getCompletionVersion(),
                authorized.lesson().getVersion());

        // Preserve the single-document bookmark from records created before this
        // feature.
        if (progress.getDocumentId() != null) {
            var positions = new LinkedHashMap<>(
                    progress.getDocumentPositions() == null ? Map.of() : progress.getDocumentPositions());
            positions.putIfAbsent(progress.getDocumentId().toString(), new LessonProgress.DocumentPosition(
                    Math.max(1, progress.getDocumentPage() == null ? 1 : progress.getDocumentPage()),
                    nonNegative(progress.getScrollOffset())));
            progress.setDocumentPositions(positions);
        }

        if (request.getLastPositionSeconds() != null)
            progress.setLastPositionSeconds(nonNegative(request.getLastPositionSeconds()));
        double previousDuration = Math.max(progress.getDurationSeconds(), nonNegative(request.getDurationSeconds()));
        // Only a video report needs the file's duration; bookmarks must not read lesson content.
        double duration = request.getWatchedSeconds() == null && request.getDurationSeconds() == null ? previousDuration
                : durations.durationOf(lessonRepository.findVideoUrl(lessonId)).orElse(previousDuration);
        double watched = Math.max(progress.getWatchedSeconds(),
                Math.min(nonNegative(request.getWatchedSeconds()), progress.getWatchedSeconds() + watchAllowance(progress)));
        progress.setDurationSeconds(duration);
        progress.setWatchedSeconds(duration > 0 ? Math.min(watched, duration) : watched);
        if (request.getContentType() != null) {
            if ("DOCUMENT".equals(request.getContentType()) && (request.getDocumentId() == null
                    || !documents.existsByIdAndLessonId(request.getDocumentId(), lessonId)))
                throw new BadRequestException("Tài liệu không thuộc bài học");
            progress.setContentType(request.getContentType());
            progress.setDocumentId("DOCUMENT".equals(request.getContentType()) ? request.getDocumentId() : null);
            progress.setDocumentPage(request.getDocumentPage());
            progress.setScrollOffset(request.getScrollOffset());
            if ("DOCUMENT".equals(request.getContentType())) {
                var positions = new LinkedHashMap<>(
                        progress.getDocumentPositions() == null ? Map.of() : progress.getDocumentPositions());
                var previous = positions.get(request.getDocumentId().toString());
                positions.put(request.getDocumentId().toString(), new LessonProgress.DocumentPosition(
                        Math.max(1, request.getDocumentPage() == null ? 1 : request.getDocumentPage()),
                        nonNegative(request.getScrollOffset()), Boolean.TRUE.equals(request.getDocumentCompleted())
                                || previous != null && Boolean.TRUE.equals(previous.completed())));
                progress.setDocumentPositions(positions);
            }
        } else if (request.getLastPositionSeconds() != null)
            progress.setContentType("VIDEO");
        if ("TEXT".equals(request.getContentType()) && Boolean.TRUE.equals(request.getTextCompleted()))
            progress.setTextCompleted(true);
        // Always evaluate current learning requirements: old supporting-file blockers
        // must disappear even when the bookmark and lesson version did not change.
        boolean contentWasComplete = progress.isContentCompleted();
        progress.setContentCompleted(currentContentComplete(authorized.lesson(), progress));
        if (!lessonCompleted) {
            // A lesson with nothing required and no required quiz waits for the learner's own
            // "mark complete": opening it must not award completion.
            if (progress.isContentCompleted()
                    && (lesson.isQuizRequired() ? LearningPolicy.quizPassed(user, lesson)
                            : requirements(authorized.lesson()).anyRequired())) {
                recordCompletion(user, lesson);
                lessonCompleted = true;
            }
        }
        progress.setCompletionVersion(authorized.lesson().getVersion());
        // Do not force an UPDATE for identical bookmarks. Access-only touches are
        // bounded.
        if (versionChanged || contentWasComplete != progress.isContentCompleted()
                || !before.equals(toDto(progress)) || progress.getUpdatedAt() == null
                || progress.getUpdatedAt().isBefore(LocalDateTime.now().minusSeconds(60))) {
            progress.setUpdatedAt(LocalDateTime.now());
            progressRepository.save(progress);
        }
        var dto = toDto(progress);
        dto.setLessonCompleted(lessonCompleted);
        return dto;
    }

    /** Watched time can grow only as fast as the fastest playback speed since the last saved progress. */
    private double watchAllowance(LessonProgress progress) {
        if (progress.getUpdatedAt() == null)
            return watchGraceSeconds;
        double elapsed = java.time.Duration.between(progress.getUpdatedAt(), LocalDateTime.now()).toMillis() / 1000.0;
        return Math.max(0, elapsed) * MAX_PLAYBACK_RATE + watchGraceSeconds;
    }

    @Transactional
    public void completeContent(String username, String lessonId) {
        var authorized = access.requireLesson(username, lessonId);
        User user = authorized.user();
        userRepository.lockForLearning(user.getId()).orElseThrow(() -> new ResourceNotFoundException("User"));
        Lesson lesson = lessonRepository.getReferenceById(lessonId);
        LessonProgress progress = progressRepository.findByUserIdAndLessonId(user.getId(), lessonId)
                .orElseGet(() -> LessonProgress.builder().user(user).lesson(lesson).build());
        progress.setContentCompleted(currentContentComplete(authorized.lesson(), progress));
        if (!progress.isContentCompleted())
            throw new BadRequestException(LessonContentPolicy.incompleteMessage(requirements(authorized.lesson())));
        progress.setUpdatedAt(LocalDateTime.now());
        progressRepository.save(progress);
    }

    public void recordCompletion(User user, Lesson lesson) {
        userRepository.lockForLearning(user.getId()).orElseThrow();
        if (userRepository.findCompletedLessonIds(user.getId(), lesson.getCourse().getId()).contains(lesson.getId()))
            return;
        user.getCompletedLessons().add(lesson);
        user.setPoints(user.getPoints() + 100);
        LearningPolicy.recordCourseCompletion(user, lesson.getCourse());
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean contentCompleted(User user, String lessonId) {
        // No saved progress is not "incomplete" when the lesson requires no content.
        var progress = progressRepository.findByUserIdAndLessonId(user.getId(), lessonId)
                .orElseGet(() -> LessonProgress.builder().build());
        return lessonRepository.findAccess(lessonId).map(row -> currentContentComplete(row, progress)).orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<String, LessonProgressDto> getAllProgress(User user) {
        Map<String, LessonProgressDto> result = new LinkedHashMap<>();
        var saved = progressRepository.findByUserId(user.getId());
        if (saved.isEmpty()) return result;
        var lessonIds = saved.stream().map(p -> p.getLesson().getId()).toList();
        var requirements = lessonRepository.findProgressRequirements(lessonIds).stream().collect(
                java.util.stream.Collectors.toMap(LessonAccessView::getId, r -> r));
        var slides = documents.findSlideIds(lessonIds).stream().collect(java.util.stream.Collectors.groupingBy(
                LessonDocumentRepository.SlideId::getLessonId,
                java.util.stream.Collectors.mapping(LessonDocumentRepository.SlideId::getId,
                        java.util.stream.Collectors.toList())));
        for (LessonProgress progress : saved) {
            var dto = toDto(progress);
            var row = requirements.get(progress.getLesson().getId());
            dto.setContentCompleted(row != null && LessonContentPolicy.complete(requirements(row,
                    slides.getOrDefault(progress.getLesson().getId(), java.util.List.of())), progress));
            result.put(progress.getLesson().getId(), dto);
        }
        return result;
    }

    @Transactional
    public void resetProgress(Long userId, String lessonId) {
        userRepository.lockForLearning(userId).orElseThrow(() -> new ResourceNotFoundException("User"));
        progressRepository.findByUserIdAndLessonId(userId, lessonId)
                .ifPresent(progressRepository::delete);
    }

    private LessonProgressDto toDto(LessonProgress progress) {
        return LessonProgressDto.builder()
                .lessonId(progress.getLesson().getId())
                .lastPositionSeconds(nonNegative(progress.getLastPositionSeconds()))
                .watchedSeconds(nonNegative(progress.getWatchedSeconds()))
                .durationSeconds(nonNegative(progress.getDurationSeconds()))
                .updatedAt(progress.getUpdatedAt()).contentType(progress.getContentType())
                .documentId(progress.getDocumentId())
                .documentPage(progress.getDocumentPage()).scrollOffset(progress.getScrollOffset())
                .documentPositions(progress.getDocumentPositions())
                .textCompleted(progress.isTextCompleted())
                .contentCompleted(progress.isContentCompleted())
                .build();
    }

    private double nonNegative(Double value) {
        return value == null || !Double.isFinite(value) ? 0D : Math.max(0D, value);
    }

    private boolean currentContentComplete(LessonAccessView row,
            LessonProgress progress) {
        // Skips the slide query when required text or video already decides the answer.
        boolean text = Boolean.TRUE.equals(row.getHasText()) && LessonContentPolicy.required(row.getTextRequired());
        boolean video = Boolean.TRUE.equals(row.getHasVideo()) && LessonContentPolicy.required(row.getVideoRequired());
        if (text && !progress.isTextCompleted() || video && !LessonContentPolicy.videoWatched(progress)) return false;
        return LessonContentPolicy.complete(requirements(row), progress);
    }

    private LessonContentPolicy.Requirements requirements(LessonAccessView row) {
        return requirements(row, documents.findAllByLessonIdAndPurpose(row.getId(), DocumentPurpose.SLIDE).stream()
                .map(LessonDocumentRepository.IdOnly::getId).toList());
    }

    private LessonContentPolicy.Requirements requirements(LessonAccessView row, java.util.List<Long> slideIds) {
        return LessonContentPolicy.requirements(Boolean.TRUE.equals(row.getHasText()), row.getTextRequired(),
                Boolean.TRUE.equals(row.getHasVideo()), row.getVideoRequired(), slideIds, row.getSlideRequired());
    }
}
