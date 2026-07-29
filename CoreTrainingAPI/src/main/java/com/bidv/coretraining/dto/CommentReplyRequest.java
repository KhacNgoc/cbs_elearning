package com.bidv.coretraining.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommentReplyRequest {
    @NotBlank(message = "Reply content cannot be empty")
    private String content;

    // Can point to the root comment or to another reply in the same thread.
    private String replyToCommentId;
}
