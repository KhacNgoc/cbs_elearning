package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.JwtAuthenticationResponse;
import com.bidv.coretraining.dto.LoginRequest;
import com.bidv.coretraining.dto.RegisterRequest;
import com.bidv.coretraining.dto.ForgotPasswordRequest;
import com.bidv.coretraining.dto.ResetPasswordOtpRequest;
import com.bidv.coretraining.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<JwtAuthenticationResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        JwtAuthenticationResponse response = authService.authenticateUser(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.registerUser(registerRequest);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/forgot-password-otp")
    public ResponseEntity<String> forgotPasswordOtp(@Valid @RequestBody ForgotPasswordRequest forgotRequest) {
        authService.sendPasswordResetOtp(forgotRequest);
        return ResponseEntity.ok("Mã xác thực OTP đã được gửi!");
    }

    @PostMapping("/reset-password-otp")
    public ResponseEntity<String> resetPasswordOtp(@Valid @RequestBody ResetPasswordOtpRequest resetRequest) {
        authService.resetPasswordWithOtp(resetRequest);
        return ResponseEntity.ok("Đặt lại mật khẩu thành công!");
    }

    @PostMapping("/reset-password-direct")
    public ResponseEntity<String> resetPasswordDirect(@Valid @RequestBody com.bidv.coretraining.dto.ResetPasswordDirectRequest resetRequest) {
        authService.resetPasswordDirect(resetRequest);
        return ResponseEntity.ok("Đặt lại mật khẩu thành công!");
    }
}
