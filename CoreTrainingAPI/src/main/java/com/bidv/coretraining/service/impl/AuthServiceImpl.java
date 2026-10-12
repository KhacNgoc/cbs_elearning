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
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<org.springframework.mail.javamail.JavaMailSender> mailSender;
    @org.springframework.beans.factory.annotation.Value("${app.password-reset.from:no-reply@localhost}") private String mailFrom;


    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @org.springframework.beans.factory.annotation.Autowired private com.bidv.coretraining.service.PermissionCache permissionCache;
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

    @Override @Transactional
    public void revokeSessions(String username) {
        User u=userRepository.findByUsername(username).orElseThrow(()->new BadRequestException("Tài khoản không tồn tại"));
        u.setTokenVersion(u.getTokenVersion()+1);userRepository.save(u);
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

        return JwtAuthenticationResponse.builder().permissionRevision(permissionCache.revision())
                .accessToken(jwt)
                .role(user.getRole())
                .roles(user.effectiveRoles()).roleNames(com.bidv.coretraining.security.AccessPolicy.roleNames(user)).systemAdmin(com.bidv.coretraining.security.AccessPolicy.system(user)).permissions(com.bidv.coretraining.security.AccessPolicy.permissions(user))
                .unitId(user.getOrganizationUnit()==null?null:user.getOrganizationUnit().getId())
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

    @Override @Transactional
    public void sendPasswordResetOtp(com.bidv.coretraining.dto.ForgotPasswordRequest request) {
        var sender=mailSender.getIfAvailable();
        if(sender==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình dịch vụ gửi email. Liên hệ quản trị viên để đặt lại mật khẩu.");
        var found=userRepository.findByUsername(request.getUsername());
        if(found.isEmpty()) return;
        User user=found.get();
        if(!user.activeAccount() || user.getEmail()==null || !user.getEmail().equalsIgnoreCase(request.getEmail())) return;
        PasswordResetOtp otp=passwordResetOtpRepository.findByUser(user).orElseGet(()->PasswordResetOtp.builder().user(user).build());
        if(otp.getExpiryDate()!=null && otp.getExpiryDate().isAfter(java.time.LocalDateTime.now().plusMinutes(4))) return;
        String code=String.format("%06d",new java.security.SecureRandom().nextInt(1_000_000));
        otp.setOtpCode(passwordEncoder.encode(code));otp.setAttempts(0);otp.setExpiryDate(java.time.LocalDateTime.now().plusMinutes(5));
        org.springframework.mail.SimpleMailMessage message=new org.springframework.mail.SimpleMailMessage();
        message.setFrom(mailFrom);message.setTo(user.getEmail());message.setSubject("Mã xác minh đặt lại mật khẩu");
        message.setText("Mã xác minh: "+code+". Mã có hiệu lực trong 5 phút. Không chia sẻ mã này.");
        try {sender.send(message);} catch(org.springframework.mail.MailException ex) {throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Không gửi được email xác minh. Vui lòng thử lại sau.");}
        passwordResetOtpRepository.save(otp);
    }

    @Override @Transactional(noRollbackFor=BadRequestException.class)
    public void resetPasswordWithOtp(com.bidv.coretraining.dto.ResetPasswordOtpRequest request) {
        User user=userRepository.findByUsername(request.getUsername()).orElseThrow(()->new BadRequestException("Mã xác minh không hợp lệ hoặc đã hết hạn"));
        PasswordResetOtp otp=passwordResetOtpRepository.findByUser(user).orElseThrow(()->new BadRequestException("Mã xác minh không hợp lệ hoặc đã hết hạn"));
        if(!user.activeAccount() || otp.getAttempts()>=5 || !otp.getExpiryDate().isAfter(java.time.LocalDateTime.now())) throw new BadRequestException("Mã xác minh không hợp lệ hoặc đã hết hạn");
        otp.setAttempts(otp.getAttempts()+1);passwordResetOtpRepository.save(otp);
        if(request.getOtp()==null || !passwordEncoder.matches(request.getOtp(),otp.getOtpCode())) throw new BadRequestException("Mã xác minh không hợp lệ hoặc đã hết hạn");
        if(request.getNewPassword()==null || request.getNewPassword().length()<8) throw new BadRequestException("Mật khẩu phải có tối thiểu 8 ký tự");
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));user.setTokenVersion(user.getTokenVersion()+1);user.setMustChangePassword(false);userRepository.save(user);
        passwordResetOtpRepository.delete(otp);
    }

    @Override
    public void resetPasswordDirect(com.bidv.coretraining.dto.ResetPasswordDirectRequest request) {
        throw new org.springframework.security.access.AccessDeniedException("Đặt lại mật khẩu phải dùng mã xác minh một lần");
    }
}
