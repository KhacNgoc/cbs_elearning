package com.bidv.coretraining.video.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VideoNoteRequest {

    @NotNull(message = "Video timestamp cannot be null")
    @Min(value = 0, message = "Video timestamp cannot be negative")
    private Integer timestampSeconds;

    @NotBlank(message = "Note content cannot be empty")
    @Size(max = 2000, message = "Note content cannot exceed 2000 characters")
    private String content;
}
