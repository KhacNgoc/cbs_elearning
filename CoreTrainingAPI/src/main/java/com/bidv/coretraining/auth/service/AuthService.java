package com.bidv.coretraining.auth.service;

import com.bidv.coretraining.auth.dto.JwtAuthenticationResponse;
import com.bidv.coretraining.auth.dto.LoginRequest;
import com.bidv.coretraining.auth.dto.RegisterRequest;

public interface AuthService {
    void revokeSessions(String username);

    JwtAuthenticationResponse authenticateUser(LoginRequest loginRequest);

    JwtAuthenticationResponse session(com.bidv.coretraining.security.CustomUserDetails principal);

    /** Issues a fresh access token after the user's token version has changed. */
    String reissueToken(String username);

    void registerUser(RegisterRequest registerRequest);

    void sendPasswordResetOtp(com.bidv.coretraining.auth.dto.ForgotPasswordRequest request);

    void resetPasswordWithOtp(com.bidv.coretraining.auth.dto.ResetPasswordOtpRequest request);

    void resetPasswordDirect(com.bidv.coretraining.auth.dto.ResetPasswordDirectRequest request);
}
