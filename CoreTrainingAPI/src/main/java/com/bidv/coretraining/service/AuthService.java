package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.JwtAuthenticationResponse;
import com.bidv.coretraining.dto.LoginRequest;
import com.bidv.coretraining.dto.RegisterRequest;

public interface AuthService {
    void revokeSessions(String username);

    JwtAuthenticationResponse authenticateUser(LoginRequest loginRequest);

    JwtAuthenticationResponse session(com.bidv.coretraining.security.CustomUserDetails principal);

    /** Issues a fresh access token after the user's token version has changed. */
    String reissueToken(String username);

    void registerUser(RegisterRequest registerRequest);

    void sendPasswordResetOtp(com.bidv.coretraining.dto.ForgotPasswordRequest request);

    void resetPasswordWithOtp(com.bidv.coretraining.dto.ResetPasswordOtpRequest request);

    void resetPasswordDirect(com.bidv.coretraining.dto.ResetPasswordDirectRequest request);
}
