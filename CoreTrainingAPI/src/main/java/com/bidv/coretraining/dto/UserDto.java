package com.bidv.coretraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private String username;
    private String password; // Only used when setting/updating password
    private String fullName;
    private String avatarColor;
    private String role; // e.g., "ROLE_USER", "ROLE_ADMIN"
    private Integer points;
    private String userGroupId;
    private String employeeId;
    private String email;
}
