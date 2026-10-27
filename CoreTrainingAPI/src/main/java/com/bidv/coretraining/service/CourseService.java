package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.CommentDto;
import com.bidv.coretraining.dto.CommentRequest;
import com.bidv.coretraining.dto.CommentReplyRequest;
import com.bidv.coretraining.dto.LessonDto;
import com.bidv.coretraining.dto.CourseDto;
import com.bidv.coretraining.dto.VideoNoteDto;
import com.bidv.coretraining.dto.VideoNoteRequest;

import java.util.List;

public interface CourseService {
    List<CourseDto> getAllCourses();

    CourseDto getCourseById(String id);

    LessonDto getLessonById(String id);

    com.bidv.coretraining.dto.CommentPageDto getComments(String id, int page, int size);

    com.bidv.coretraining.dto.CommentReplyPageDto getReplies(String lessonId, Long rootCommentId, String after);

    CommentDto addCommentToLesson(String lessonId, CommentRequest commentRequest, String username);

    CommentDto replyToComment(String lessonId, Long commentId, CommentReplyRequest request, String username);

    void deleteComment(String lessonId, Long commentId, String username, boolean isAdmin);

    List<VideoNoteDto> getVideoNotes(String lessonId, String username);

    VideoNoteDto createVideoNote(String lessonId, VideoNoteRequest request, String username);

    VideoNoteDto updateVideoNote(String lessonId, Long noteId, VideoNoteRequest request, String username);

    void deleteVideoNote(String lessonId, Long noteId, String username);
}
