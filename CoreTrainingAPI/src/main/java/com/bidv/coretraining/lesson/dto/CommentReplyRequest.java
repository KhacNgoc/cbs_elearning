package com.bidv.coretraining.lesson.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommentReplyRequest {
    @NotBlank(message = "Reply content cannot be empty")
    @jakarta.validation.constraints.Size(max = 5000, message = "Nội dung tối đa 5000 ký tự")
    private String content;

    // Can point to the root comment or to another reply in the same thread.
    private Long replyToCommentId;
}
