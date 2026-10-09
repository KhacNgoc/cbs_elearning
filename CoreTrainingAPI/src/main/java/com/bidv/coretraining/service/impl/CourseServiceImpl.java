package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.Comment;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.VideoNote;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.CommentRepository;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.CourseRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.repository.VideoNoteRepository;
import com.bidv.coretraining.service.CourseService;
import com.bidv.coretraining.service.NotificationService;
import com.bidv.coretraining.entity.NotificationType;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final VideoNoteRepository videoNoteRepository;
    private final CourseMapper courseMapper;
    private final NotificationService notificationService;

    public CourseServiceImpl(CourseRepository courseRepository, LessonRepository lessonRepository,
                             CommentRepository commentRepository, UserRepository userRepository,
                             VideoNoteRepository videoNoteRepository, CourseMapper courseMapper,
                             NotificationService notificationService) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.videoNoteRepository = videoNoteRepository;
        this.courseMapper = courseMapper;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseDto> getAllCourses() {
        boolean admin=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));
        List<Course> courses = courseRepository.findAll().stream().filter(c->admin || "PUBLISHED".equals(c.getStatus())).toList();
        List<CourseDto> result=courseMapper.toCourseDtoList(courses);
        if(!admin)result.forEach(c->c.getLessons().forEach(com.bidv.coretraining.service.TrainingStructureService::outlineOnly));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDto getCourseById(String id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        boolean admin=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));
        if(!admin && !"PUBLISHED".equals(course.getStatus()))throw new ResourceNotFoundException("Course","id",id);
        CourseDto result=courseMapper.toCourseDto(course);
        if(!admin)result.getLessons().forEach(com.bidv.coretraining.service.TrainingStructureService::outlineOnly);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public LessonDto getLessonById(String id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        return courseMapper.toLessonDto(lesson);
    }

    @Override
    @Transactional
    public CommentDto addCommentToLesson(String lessonId, CommentRequest commentRequest, String username) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));

        String avatar = String.valueOf(username.charAt(0)).toUpperCase();

        Comment comment = Comment.builder()
                .username(username)
                .avatar(avatar)
                .rating(commentRequest.getRating())
                .content(commentRequest.getContent())
                .createdAt(LocalDateTime.now())
                .lesson(lesson)
                .build();

        Comment savedComment = commentRepository.save(comment);
        return courseMapper.toCommentDto(savedComment);
    }

    @Override
    @Transactional
    public CommentDto replyToComment(String lessonId, Long commentId,
                                     CommentReplyRequest request, String username) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        Comment requestedParent = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));
        if (!requestedParent.getLesson().getId().equals(lessonId)) {
            throw new IllegalArgumentException("Bình luận không thuộc bài học này");
        }

        Comment replyTarget = requestedParent;
        if (request.getReplyToCommentId() != null) {
            replyTarget = commentRepository.findById(request.getReplyToCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Comment", "id", request.getReplyToCommentId()));
            if (!replyTarget.getLesson().getId().equals(lessonId)) {
                throw new IllegalArgumentException("Phản hồi không thuộc bài học này");
            }
        }
        Comment root = requestedParent.getParent() == null
                ? requestedParent
                : requestedParent.getParent();

        Comment reply = Comment.builder()
                .username(username)
                .avatar(String.valueOf(username.charAt(0)).toUpperCase())
                .content(request.getContent().trim())
                .createdAt(LocalDateTime.now())
                .lesson(lesson)
                .parent(root)
                .replyToUsername(replyTarget.getUsername())
                .build();
        Comment savedReply = commentRepository.save(reply);
        if (!replyTarget.getUsername().equals(username)) {
            notificationService.create(
                    replyTarget.getUsername(),
                    NotificationType.COMMENT_REPLY,
                    "Có phản hồi mới cho bình luận của bạn",
                    username + " đã phản hồi bình luận của bạn trong bài "
                            + lesson.getTitle(),
                    "/lesson/" + lessonId);
        }
        return courseMapper.toCommentDto(savedReply);
    }

    @Override
    @Transactional
    public void deleteComment(
            String lessonId, Long commentId, String username, boolean isAdmin) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));
        if (!comment.getLesson().getId().equals(lessonId)) {
            throw new ResourceNotFoundException("Comment", "id", commentId);
        }
        if (!isAdmin && !comment.getUsername().equals(username)) {
            throw new AccessDeniedException("Bạn chỉ có thể gỡ bình luận của chính mình");
        }
        commentRepository.delete(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoNoteDto> getVideoNotes(String lessonId, String username) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", "id", lessonId);
        }

        return videoNoteRepository
                .findAllByLessonIdAndUserUsernameOrderByTimestampSecondsAscCreatedAtAsc(lessonId, username)
                .stream()
                .map(this::toVideoNoteDto)
                .toList();
    }

    @Override
    @Transactional
    public VideoNoteDto createVideoNote(String lessonId, VideoNoteRequest request, String username) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        LocalDateTime now = LocalDateTime.now();

        VideoNote note = VideoNote.builder()
                .lesson(lesson)
                .user(user)
                .timestampSeconds(request.getTimestampSeconds())
                .content(request.getContent().trim())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toVideoNoteDto(videoNoteRepository.save(note));
    }

    @Override
    @Transactional
    public VideoNoteDto updateVideoNote(
            String lessonId, Long noteId, VideoNoteRequest request, String username) {
        VideoNote note = findOwnedVideoNote(lessonId, noteId, username);
        note.setTimestampSeconds(request.getTimestampSeconds());
        note.setContent(request.getContent().trim());
        note.setUpdatedAt(LocalDateTime.now());
        return toVideoNoteDto(videoNoteRepository.save(note));
    }

    @Override
    @Transactional
    public void deleteVideoNote(String lessonId, Long noteId, String username) {
        videoNoteRepository.delete(findOwnedVideoNote(lessonId, noteId, username));
    }

    private VideoNote findOwnedVideoNote(String lessonId, Long noteId, String username) {
        return videoNoteRepository.findByIdAndLessonIdAndUserUsername(noteId, lessonId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Video note", "id", noteId));
    }

    private VideoNoteDto toVideoNoteDto(VideoNote note) {
        return VideoNoteDto.builder()
                .id(note.getId())
                .lessonId(note.getLesson().getId())
                .timestampSeconds(note.getTimestampSeconds())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
