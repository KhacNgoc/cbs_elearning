package com.bidv.coretraining.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommentRequest {
    // Accepted for old clients, but new lesson comments no longer store stars.
    private Integer rating;

    @NotBlank(message = "Comment content cannot be empty")
    private String content;
}
