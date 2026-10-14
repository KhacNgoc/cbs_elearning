package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LessonProgressDto;
import com.bidv.coretraining.dto.LessonProgressRequest;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.LessonProgress;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.LessonProgressRepository;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LessonProgressService {
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;

    public LessonProgressService(UserRepository userRepository,
                                 LessonRepository lessonRepository,
                                 LessonProgressRepository progressRepository) {
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
    }

    @Transactional
    public LessonProgressDto saveProgress(String username, String lessonId, LessonProgressRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        LearningPolicy.assertLessonAccess(user,lesson);
        LessonProgress progress = progressRepository.findByUserIdAndLessonId(user.getId(), lessonId)
                .orElseGet(() -> LessonProgress.builder().user(user).lesson(lesson).build());

        if(request.getLastPositionSeconds()!=null)progress.setLastPositionSeconds(nonNegative(request.getLastPositionSeconds()));
        progress.setWatchedSeconds(Math.max(progress.getWatchedSeconds(), nonNegative(request.getWatchedSeconds())));
        progress.setDurationSeconds(Math.max(progress.getDurationSeconds(), nonNegative(request.getDurationSeconds())));
        if(request.getContentType()!=null) {
            if("DOCUMENT".equals(request.getContentType()) && lesson.getDocuments().stream().noneMatch(d->java.util.Objects.equals(d.getId(),request.getDocumentId())))
                throw new com.bidv.coretraining.exception.BadRequestException("Tài liệu không thuộc bài học");
            progress.setContentType(request.getContentType());
            progress.setDocumentId("DOCUMENT".equals(request.getContentType())?request.getDocumentId():null);
            progress.setDocumentPage(request.getDocumentPage());progress.setScrollOffset(request.getScrollOffset());
        } else if(request.getLastPositionSeconds()!=null)progress.setContentType("VIDEO");
        if(progress.getDurationSeconds()>0 && progress.getWatchedSeconds()>=progress.getDurationSeconds()*0.9) progress.setContentCompleted(true);
        progress.setUpdatedAt(LocalDateTime.now());
        return toDto(progressRepository.save(progress));
    }

    @Transactional
    public void completeContent(String username,String lessonId) {
        User user=userRepository.findByUsername(username).orElseThrow(()->new ResourceNotFoundException("User","username",username));
        Lesson lesson=lessonRepository.findById(lessonId).orElseThrow(()->new ResourceNotFoundException("Lesson","id",lessonId));
        LearningPolicy.assertLessonAccess(user,lesson);
        LessonProgress progress=progressRepository.findByUserIdAndLessonId(user.getId(),lessonId)
                .orElseGet(()->LessonProgress.builder().user(user).lesson(lesson).build());
        if(lesson.getVideoUrl()!=null && !lesson.getVideoUrl().isBlank() && !progress.isContentCompleted())
            throw new com.bidv.coretraining.exception.BadRequestException("Hãy học xong nội dung video trước khi hoàn thành bài");
        progress.setContentCompleted(true);progress.setUpdatedAt(LocalDateTime.now());progressRepository.save(progress);
    }
    @Transactional(readOnly=true)
    public boolean contentCompleted(User user,String lessonId) {
        return progressRepository.findByUserIdAndLessonId(user.getId(),lessonId).map(LessonProgress::isContentCompleted).orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<String, LessonProgressDto> getAllProgress(User user) {
        Map<String, LessonProgressDto> result = new LinkedHashMap<>();
        for (LessonProgress progress : progressRepository.findByUserId(user.getId())) {
            result.put(progress.getLesson().getId(), toDto(progress));
        }
        return result;
    }

    @Transactional
    public void resetProgress(Long userId, String lessonId) {
        progressRepository.findByUserIdAndLessonId(userId, lessonId)
                .ifPresent(progressRepository::delete);
    }

    private LessonProgressDto toDto(LessonProgress progress) {
        return LessonProgressDto.builder()
                .lessonId(progress.getLesson().getId())
                .lastPositionSeconds(nonNegative(progress.getLastPositionSeconds()))
                .watchedSeconds(nonNegative(progress.getWatchedSeconds()))
                .durationSeconds(nonNegative(progress.getDurationSeconds()))
                .updatedAt(progress.getUpdatedAt()).contentType(progress.getContentType()).documentId(progress.getDocumentId())
                .documentPage(progress.getDocumentPage()).scrollOffset(progress.getScrollOffset()).contentCompleted(progress.isContentCompleted())
                .build();
    }

    private double nonNegative(Double value) {
        return value == null ? 0D : Math.max(0D, value);
    }
}
