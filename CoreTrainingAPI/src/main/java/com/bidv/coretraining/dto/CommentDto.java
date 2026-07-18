package com.bidv.coretraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDto {
    private String id;
    private String user; // Maps to username
    private String avatar; // Initial of user
    private Integer rating;
    private String content;
    private String date; // Format YYYY-MM-DD
}
