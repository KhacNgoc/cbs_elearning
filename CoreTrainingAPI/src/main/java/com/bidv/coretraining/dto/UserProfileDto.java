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
    private java.util.Map<String, String> roleNames;
    private boolean systemAdmin;
    private java.util.Set<String> roles;
    private java.util.Set<String> permissions;
    private String unitId;
    private String unitName;
    private java.util.Set<String> scopeUnitIds;
    private String status;
    private String learnerType;
    private Integer points;
}
