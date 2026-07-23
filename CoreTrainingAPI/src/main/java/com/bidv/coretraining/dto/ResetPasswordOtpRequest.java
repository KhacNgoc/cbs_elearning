package com.bidv.coretraining.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordOtpRequest {

    @NotBlank(message = "Username cannot be blank")
    private String username;

    @NotBlank(message = "OTP cannot be blank")
    @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
    private String otp;

    @NotBlank(message = "New password cannot be blank")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String newPassword;
}
