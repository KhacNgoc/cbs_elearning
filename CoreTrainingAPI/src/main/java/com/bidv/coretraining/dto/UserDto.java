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
    private java.util.Set<String> allowedActions;
    private String username;
    private String password; // Only used when setting/updating password
    private String fullName;
    private String avatarColor;
    private String role;
    private java.util.Map<String, String> roleNames;
    private java.util.Set<String> roles;
    private java.util.Set<String> permissions;
    private String unitId;
    private java.util.Set<String> scopeUnitIds;
    private String status;
    private String learnerType; // e.g., "ROLE_USER", "ROLE_ADMIN"
    private Integer points;
    private java.util.List<String> userGroupIds;
    private String groupNames;
    private String employeeId;
    private String email;
}
