package com.bidv.coretraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String employeeId;
    private String avatarColor;
    private String role;
    private Integer points;
    private String groupId;
    private String groupName;
}
