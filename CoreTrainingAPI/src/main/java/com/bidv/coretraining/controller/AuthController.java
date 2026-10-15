package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.JwtAuthenticationResponse;
import com.bidv.coretraining.dto.LoginRequest;
import com.bidv.coretraining.dto.RegisterRequest;
import com.bidv.coretraining.dto.ForgotPasswordRequest;
import com.bidv.coretraining.dto.ResetPasswordOtpRequest;
import com.bidv.coretraining.service.AuthService;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.security.JwtCookieService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.service.PermissionCache permissionCache;

    private final AuthService authService;
    private final JwtCookieService jwtCookieService;

    public AuthController(AuthService authService, JwtCookieService jwtCookieService) {
        this.authService = authService;
        this.jwtCookieService = jwtCookieService;
    }

    @org.springframework.web.bind.annotation.GetMapping("/csrf")
    public java.util.Map<String, String> csrf(org.springframework.security.web.csrf.CsrfToken token) {
        return java.util.Map.of("token", token.getToken());
    }

    @PostMapping("/login")
    public ResponseEntity<JwtAuthenticationResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        JwtAuthenticationResponse response = authService.authenticateUser(loginRequest);
        String token = response.getAccessToken();
        response.setAccessToken(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookieService.create(token))
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomUserDetails user) {
        if (user != null)
            authService.revokeSessions(user.getUsername());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, jwtCookieService.clear())
                .build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/session")
    public ResponseEntity<JwtAuthenticationResponse> session(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(JwtAuthenticationResponse.builder().permissionRevision(permissionCache.revision())
                .role(user.getUser().getRole())
                .roles(user.getUser().effectiveRoles())
                .roleNames(com.bidv.coretraining.security.AccessPolicy.roleNames(user.getUser()))
                .systemAdmin(com.bidv.coretraining.security.AccessPolicy.system(user.getUser()))
                .permissions(com.bidv.coretraining.security.AccessPolicy.permissions(user.getUser()))
                .unitId(user.getUser().getOrganizationUnit() == null ? null
                        : user.getUser().getOrganizationUnit().getId())
                .scopeUnitIds(user.getUser().getScopeUnitIds()).status(user.getUser().getStatus())
                .learnerType(user.getUser().getLearnerType())
                .username(user.getUsername())
                .fullName(user.getUser().getFullName())
                .mustChangePassword(user.getUser().isMustChangePassword())
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.registerUser(registerRequest);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/forgot-password-otp")
    public ResponseEntity<String> forgotPasswordOtp(@Valid @RequestBody ForgotPasswordRequest forgotRequest) {
        authService.sendPasswordResetOtp(forgotRequest);
        return ResponseEntity.ok("Nếu thông tin hợp lệ, mã xác minh đã được gửi đến email của bạn.");
    }

    @PostMapping("/reset-password-otp")
    public ResponseEntity<String> resetPasswordOtp(@Valid @RequestBody ResetPasswordOtpRequest resetRequest) {
        authService.resetPasswordWithOtp(resetRequest);
        return ResponseEntity.ok("Đặt lại mật khẩu thành công!");
    }

    @PostMapping("/reset-password-direct")
    public ResponseEntity<String> resetPasswordDirect(
            @Valid @RequestBody com.bidv.coretraining.dto.ResetPasswordDirectRequest resetRequest) {
        authService.resetPasswordDirect(resetRequest);
        return ResponseEntity.ok("Đặt lại mật khẩu thành công!");
    }
}
