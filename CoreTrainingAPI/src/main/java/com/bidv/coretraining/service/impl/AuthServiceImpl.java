package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.JwtAuthenticationResponse;
import com.bidv.coretraining.dto.LoginRequest;
import com.bidv.coretraining.dto.RegisterRequest;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.UserRepository;
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

    public AuthServiceImpl(AuthenticationManager authenticationManager, UserRepository userRepository,
                           PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
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
                .role("ROLE_USER")
                .points(1000) // Initial default points to match mock representation
                .build();

        userRepository.save(user);
    }
}
