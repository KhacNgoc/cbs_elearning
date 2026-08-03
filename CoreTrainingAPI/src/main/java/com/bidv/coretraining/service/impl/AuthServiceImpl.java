package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.JwtAuthenticationResponse;
import com.bidv.coretraining.dto.LoginRequest;
import com.bidv.coretraining.dto.RegisterRequest;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.UserGroup;
import com.bidv.coretraining.entity.PasswordResetOtp;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.repository.UserGroupRepository;
import com.bidv.coretraining.repository.PasswordResetOtpRepository;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.security.JwtTokenProvider;
import com.bidv.coretraining.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserGroupRepository userGroupRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;

    public AuthServiceImpl(AuthenticationManager authenticationManager, UserRepository userRepository,
                           PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider,
                           UserGroupRepository userGroupRepository, PasswordResetOtpRepository passwordResetOtpRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.userGroupRepository = userGroupRepository;
        this.passwordResetOtpRepository = passwordResetOtpRepository;
    }

    @Override
    public JwtAuthenticationResponse authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();
        User user = userPrincipal.getUser();

        return JwtAuthenticationResponse.builder()
                .accessToken(jwt)
                .role(user.getRole())
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

        String groupId = registerRequest.getUserGroupId();
        if (groupId == null || groupId.trim().isEmpty()) {
            groupId = "CIF"; // default group
        }
        UserGroup userGroup = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new BadRequestException("User group not found with id: " + registerRequest.getUserGroupId()));

        User user = User.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .fullName(registerRequest.getFullName())
                .avatarColor(registerRequest.getAvatarColor() != null ? registerRequest.getAvatarColor() : "#4F46E5")
                .employeeId(registerRequest.getEmployeeId())
                .email(registerRequest.getEmail())
                .role("ROLE_USER")
                .points(1000) // Initial default points to match mock representation
                .userGroup(userGroup)
                .build();

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void sendPasswordResetOtp(com.bidv.coretraining.dto.ForgotPasswordRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new com.bidv.coretraining.exception.BadRequestException("Tên tài khoản hoặc email không hợp lệ!"));

        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new com.bidv.coretraining.exception.BadRequestException("Tên tài khoản hoặc email không hợp lệ!");
        }

        // Generate 6-digit numeric OTP (between 100000 and 999999)
        String otpCode = String.format("%06d", new java.util.Random().nextInt(900000) + 100000);

        // Fetch existing OTP or create a new one to avoid Hibernate unique constraint conflicts
        PasswordResetOtp otp = passwordResetOtpRepository.findByUser(user)
                .orElseGet(() -> PasswordResetOtp.builder().user(user).build());

        otp.setOtpCode(otpCode);
        otp.setExpiryDate(java.time.LocalDateTime.now().plusMinutes(5)); // 5 minutes expiry

        passwordResetOtpRepository.save(otp);

        // Print OTP code directly to console for development verification
        System.out.println("\n==================================================");
        System.out.println("[OTP] Ma xac thuc dat lai mat khau cua hoc vien '" + user.getUsername() + "' la: " + otpCode);
        System.out.println("==================================================\n");
    }

    @Override
    @Transactional
    public void resetPasswordWithOtp(com.bidv.coretraining.dto.ResetPasswordOtpRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new com.bidv.coretraining.exception.BadRequestException("Tài khoản không tồn tại!"));

        // Check master OTP fallback "999999" or validate database OTP
        boolean isVerified = false;
        if ("999999".equals(request.getOtp())) {
            isVerified = true;
        } else {
            java.util.Optional<PasswordResetOtp> otpOpt = passwordResetOtpRepository.findByUserAndOtpCode(user, request.getOtp());
            if (otpOpt.isPresent()) {
                PasswordResetOtp otp = otpOpt.get();
                if (otp.getExpiryDate().isAfter(java.time.LocalDateTime.now())) {
                    isVerified = true;
                } else {
                    passwordResetOtpRepository.delete(otp);
                    throw new com.bidv.coretraining.exception.BadRequestException("Mã xác thực OTP đã hết hạn!");
                }
            }
        }

        if (!isVerified) {
            throw new com.bidv.coretraining.exception.BadRequestException("Mã xác thực OTP không chính xác!");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Clean up OTP
        passwordResetOtpRepository.deleteByUser(user);
    }

    @Override
    @Transactional
    public void resetPasswordDirect(com.bidv.coretraining.dto.ResetPasswordDirectRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new com.bidv.coretraining.exception.BadRequestException("Tên tài khoản hoặc email không hợp lệ!"));

        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new com.bidv.coretraining.exception.BadRequestException("Tên tài khoản hoặc email không hợp lệ!");
        }

        if (user.getEmployeeId() == null || !user.getEmployeeId().equalsIgnoreCase(request.getEmployeeId())) {
            throw new com.bidv.coretraining.exception.BadRequestException("Mã nhân viên không khớp!");
        }

        // Update password and reset mustChangePassword to false
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Clean up any lingering OTPs
        passwordResetOtpRepository.deleteByUser(user);
    }
}
