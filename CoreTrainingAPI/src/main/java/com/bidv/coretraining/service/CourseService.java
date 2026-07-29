package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.CommentDto;
import com.bidv.coretraining.dto.CommentRequest;
import com.bidv.coretraining.dto.CommentReplyRequest;
import com.bidv.coretraining.dto.LessonDto;
import com.bidv.coretraining.dto.TopicDto;
import com.bidv.coretraining.dto.VideoNoteDto;
import com.bidv.coretraining.dto.VideoNoteRequest;

import java.util.List;

public interface CourseService {
    List<TopicDto> getAllTopics();
    TopicDto getTopicById(String id);
    LessonDto getLessonById(String id);
    CommentDto addCommentToLesson(String lessonId, CommentRequest commentRequest, String username);
    CommentDto replyToComment(String lessonId, String commentId, CommentReplyRequest request, String username);
    List<VideoNoteDto> getVideoNotes(String lessonId, String username);
    VideoNoteDto createVideoNote(String lessonId, VideoNoteRequest request, String username);
    VideoNoteDto updateVideoNote(String lessonId, Long noteId, VideoNoteRequest request, String username);
    void deleteVideoNote(String lessonId, Long noteId, String username);
}
