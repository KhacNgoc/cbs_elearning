package com.bidv.coretraining.dto;

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
    private String username;
    private String fullName;
    private boolean mustChangePassword;
}
