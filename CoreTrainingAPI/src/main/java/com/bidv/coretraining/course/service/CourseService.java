package com.bidv.coretraining.course.service;

import com.bidv.coretraining.course.dto.CourseDto;
import com.bidv.coretraining.lesson.dto.CommentDto;
import com.bidv.coretraining.lesson.dto.CommentReplyRequest;
import com.bidv.coretraining.lesson.dto.CommentRequest;
import com.bidv.coretraining.lesson.dto.LessonDto;
import com.bidv.coretraining.video.dto.VideoNoteDto;
import com.bidv.coretraining.video.dto.VideoNoteRequest;

import java.util.List;

public interface CourseService {
    List<CourseDto> getAllCourses();

    CourseDto getCourseById(String id);

    LessonDto getLessonById(String id);

    com.bidv.coretraining.lesson.dto.CommentPageDto getComments(String id, int page, int size);

    com.bidv.coretraining.lesson.dto.CommentReplyPageDto getReplies(String lessonId, Long rootCommentId, String after);

    CommentDto addCommentToLesson(String lessonId, CommentRequest commentRequest, String username);

    CommentDto replyToComment(String lessonId, Long commentId, CommentReplyRequest request, String username);

    void deleteComment(String lessonId, Long commentId, String username);

    List<VideoNoteDto> getVideoNotes(String lessonId, String username);

    VideoNoteDto createVideoNote(String lessonId, VideoNoteRequest request, String username);

    VideoNoteDto updateVideoNote(String lessonId, Long noteId, VideoNoteRequest request, String username);

    void deleteVideoNote(String lessonId, Long noteId, String username);
}
