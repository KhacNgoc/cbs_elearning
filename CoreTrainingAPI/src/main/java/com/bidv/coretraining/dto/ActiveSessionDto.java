package com.bidv.coretraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiveSessionDto {
    private String username;
    private String fullName;
    private String avatarColor;
    private String action;
    private String detail;
    private String topic;
    private String time;
}
