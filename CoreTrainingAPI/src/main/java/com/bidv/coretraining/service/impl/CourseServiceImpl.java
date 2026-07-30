package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.Comment;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.Topic;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.VideoNote;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.CommentRepository;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.TopicRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.repository.VideoNoteRepository;
import com.bidv.coretraining.service.CourseService;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {

    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final VideoNoteRepository videoNoteRepository;
    private final CourseMapper courseMapper;

    public CourseServiceImpl(TopicRepository topicRepository, LessonRepository lessonRepository,
                             CommentRepository commentRepository, UserRepository userRepository,
                             VideoNoteRepository videoNoteRepository, CourseMapper courseMapper) {
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.videoNoteRepository = videoNoteRepository;
        this.courseMapper = courseMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicDto> getAllTopics() {
        List<Topic> topics = topicRepository.findAll();
        return courseMapper.toTopicDtoList(topics);
    }

    @Override
    @Transactional(readOnly = true)
    public TopicDto getTopicById(String id) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", id));
        return courseMapper.toTopicDto(topic);
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
        return courseMapper.toCommentDto(commentRepository.save(reply));
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
