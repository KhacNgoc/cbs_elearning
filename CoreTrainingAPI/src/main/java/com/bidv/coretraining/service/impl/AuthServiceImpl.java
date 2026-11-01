package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.service.PermissionCache;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.dto.ForgotPasswordRequest;
import com.bidv.coretraining.dto.ResetPasswordOtpRequest;
import com.bidv.coretraining.dto.ResetPasswordDirectRequest;

import com.bidv.coretraining.dto.JwtAuthenticationResponse;
import com.bidv.coretraining.dto.LoginRequest;
import com.bidv.coretraining.dto.RegisterRequest;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.PasswordResetOtp;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.repository.PasswordResetOtpRepository;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.security.JwtTokenProvider;
import com.bidv.coretraining.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.mail.javamail.JavaMailSender> mailSender;
    @org.springframework.beans.factory.annotation.Value("${app.password-reset.from:no-reply@localhost}")
    private String mailFrom;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCache permissionCache;
    private final JwtTokenProvider tokenProvider;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final UserDetailsService userDetailsService;

    public AuthServiceImpl(AuthenticationManager authenticationManager, UserRepository userRepository,
            PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider,
            PasswordResetOtpRepository passwordResetOtpRepository, UserDetailsService userDetailsService,
            org.springframework.beans.factory.ObjectProvider<org.springframework.mail.javamail.JavaMailSender> mailSender, PermissionCache permissionCache) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.passwordResetOtpRepository = passwordResetOtpRepository;
        this.userDetailsService = userDetailsService;
        this.mailSender = mailSender;
        this.permissionCache = permissionCache;
    }

    @Override
    @Transactional
    public void revokeSessions(String username) {
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("Tài khoản không tồn tại"));
        u.setTokenVersion(u.getTokenVersion() + 1);
        userRepository.save(u);
    }

    @Override
    public JwtAuthenticationResponse authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();
        return sessionResponse(userPrincipal.getUser(), jwt);
    }

    @Override
    public JwtAuthenticationResponse session(CustomUserDetails principal) {
        return sessionResponse(principal.getUser(), null);
    }

    @Override
    public String reissueToken(String username) {
        UserDetails fresh = userDetailsService.loadUserByUsername(username);
        return tokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities()));
    }

    private JwtAuthenticationResponse sessionResponse(User user, String jwt) {
        return JwtAuthenticationResponse.builder().permissionRevision(permissionCache.revision())
                .accessToken(jwt)
                .role(user.getRole())
                .roles(user.effectiveRoles()).roleNames(AccessPolicy.roleNames(user))
                .systemAdmin(AccessPolicy.system(user))
                .permissions(AccessPolicy.permissions(user))
                .unitId(user.getOrganizationUnit() == null ? null : user.getOrganizationUnit().getId())
                .scopeUnitIds(user.getScopeUnitIds()).status(user.getStatus()).learnerType(user.getLearnerType())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    @Override
    @Transactional
    public void registerUser(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new BadRequestException("Username is already taken!");
        }

        User user = User.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .fullName(registerRequest.getFullName())
                .avatarColor(registerRequest.getAvatarColor() != null ? registerRequest.getAvatarColor() : "#4F46E5")
                .employeeId(registerRequest.getEmployeeId())
                .email(registerRequest.getEmail())
                .role("ROLE_USER")
                .points(1000) // Initial default points to match mock representation
                .build();

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void sendPasswordResetOtp(ForgotPasswordRequest request) {
        var sender = mailSender.getIfAvailable();
        if (sender == null)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình dịch vụ gửi email. Liên hệ quản trị viên để đặt lại mật khẩu.");
        var found = userRepository.findByUsername(request.getUsername());
        if (found.isEmpty())
            return;
        User user = found.get();
        if (!user.activeAccount() || user.getEmail() == null || !user.getEmail().equalsIgnoreCase(request.getEmail()))
            return;
        PasswordResetOtp otp = passwordResetOtpRepository.findByUser(user)
                .orElseGet(() -> PasswordResetOtp.builder().user(user).build());
        if (otp.getExpiryDate() != null && otp.getExpiryDate().isAfter(java.time.LocalDateTime.now().plusMinutes(4)))
            return;
        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        otp.setOtpCode(passwordEncoder.encode(code));
        otp.setAttempts(0);
        otp.setExpiryDate(java.time.LocalDateTime.now().plusMinutes(5));
        org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("Mã xác minh đặt lại mật khẩu");
        message.setText("Mã xác minh: " + code + ". Mã có hiệu lực trong 5 phút. Không chia sẻ mã này.");
        try {
            sender.send(message);
        } catch (org.springframework.mail.MailException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Không gửi được email xác minh. Vui lòng thử lại sau.");
        }
        passwordResetOtpRepository.save(otp);
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public void resetPasswordWithOtp(ResetPasswordOtpRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadRequestException("Mã xác minh không hợp lệ hoặc đã hết hạn"));
        PasswordResetOtp otp = passwordResetOtpRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Mã xác minh không hợp lệ hoặc đã hết hạn"));
        if (!user.activeAccount() || otp.getAttempts() >= 5
                || !otp.getExpiryDate().isAfter(java.time.LocalDateTime.now()))
            throw new BadRequestException("Mã xác minh không hợp lệ hoặc đã hết hạn");
        otp.setAttempts(otp.getAttempts() + 1);
        passwordResetOtpRepository.save(otp);
        if (request.getOtp() == null || !passwordEncoder.matches(request.getOtp(), otp.getOtpCode()))
            throw new BadRequestException("Mã xác minh không hợp lệ hoặc đã hết hạn");
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8)
            throw new BadRequestException("Mật khẩu phải có tối thiểu 8 ký tự");
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setMustChangePassword(false);
        userRepository.save(user);
        passwordResetOtpRepository.delete(otp);
    }

    @Override
    public void resetPasswordDirect(ResetPasswordDirectRequest request) {
        throw new org.springframework.security.access.AccessDeniedException(
                "Đặt lại mật khẩu phải dùng mã xác minh một lần");
    }
}
