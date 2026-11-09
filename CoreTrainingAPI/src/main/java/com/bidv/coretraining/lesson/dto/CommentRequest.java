package com.bidv.coretraining.lesson.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommentRequest {
    // Accepted for old clients, but new lesson comments no longer store stars.
    private Integer rating;

    @NotBlank(message = "Comment content cannot be empty")
    @jakarta.validation.constraints.Size(max = 5000, message = "Nội dung tối đa 5000 ký tự")
    private String content;
}
