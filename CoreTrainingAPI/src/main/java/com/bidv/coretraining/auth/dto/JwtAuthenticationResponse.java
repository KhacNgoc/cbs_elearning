package com.bidv.coretraining.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtAuthenticationResponse {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private String role;
    private java.util.Map<String, String> roleNames;
    private long permissionRevision;
    private boolean systemAdmin;
    private java.util.Set<String> roles;
    private java.util.Set<String> permissions;
    private String unitId;
    private java.util.Set<String> scopeUnitIds;
    private String status;
    private String learnerType;
    private String username;
    private String fullName;
    private boolean mustChangePassword;
}
