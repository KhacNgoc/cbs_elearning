package com.bidv.coretraining.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordDirectRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String email;
    @NotBlank
    private String employeeId;
    @NotBlank
    private String newPassword;
}
