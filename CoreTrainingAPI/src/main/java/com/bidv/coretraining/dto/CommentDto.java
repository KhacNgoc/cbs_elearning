package com.bidv.coretraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDto {
    private Long id;
    private String user; // Maps to username
    private String avatar; // Initial of user
    private Integer rating;
    private String content;
    private String date; // Format YYYY-MM-DD
    private Long parentId;
    private String replyToUser;
    private List<CommentDto> replies;
}
