package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.JwtAuthenticationResponse;
import com.bidv.coretraining.dto.LoginRequest;
import com.bidv.coretraining.dto.RegisterRequest;

public interface AuthService {
    JwtAuthenticationResponse authenticateUser(LoginRequest loginRequest);
    void registerUser(RegisterRequest registerRequest);
    void sendPasswordResetOtp(com.bidv.coretraining.dto.ForgotPasswordRequest request);
    void resetPasswordWithOtp(com.bidv.coretraining.dto.ResetPasswordOtpRequest request);
}
