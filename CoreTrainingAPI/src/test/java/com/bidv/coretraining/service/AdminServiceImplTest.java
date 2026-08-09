package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.CourseMapper;
import com.bidv.coretraining.dto.QuizMapper;
import com.bidv.coretraining.dto.UserDto;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.UserGroup;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminServiceImplTest {
    private UserRepository userRepository;
    private UserGroupRepository userGroupRepository;
    private PasswordEncoder passwordEncoder;
    private AdminServiceImpl adminService;
    private UserGroup mockGroup;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userGroupRepository = mock(UserGroupRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);

        adminService = new AdminServiceImpl(
                mock(TopicRepository.class),
                mock(LessonRepository.class),
                mock(QuestionRepository.class),
                mock(CourseMapper.class),
                mock(QuizMapper.class),
                userRepository,
                passwordEncoder,
                mock(TopicEnrollmentRequestRepository.class),
                mock(NotificationService.class),
                userGroupRepository
        );

        mockGroup = UserGroup.builder().id("CIF").name("Khách hàng CIF").build();
        when(userGroupRepository.findById("CIF")).thenReturn(Optional.of(mockGroup));
    }

    @Test
    void createUser_whenUsernameExists_throwsDataIntegrityViolationException() {
        UserDto dto = UserDto.builder()
                .username("duplicate_user")
                .fullName("Duplicate User")
                .role("ROLE_USER")
                .userGroupId("CIF")
                .build();

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("ukr43af9ap4edm43mmtq01oddj6 constraint violation"));

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> adminService.createUser(dto)
        );
        verify(userRepository, times(1)).saveAndFlush(any(User.class));
    }

    @Test
    void createUser_whenUserGroupIdMissing_throwsBadRequestException() {
        UserDto dto = UserDto.builder()
                .username("new_user")
                .fullName("New User")
                .role("ROLE_USER")
                .userGroupId(null)
                .build();

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> adminService.createUser(dto)
        );

        assertEquals("Nhóm nghiệp vụ là bắt buộc", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_whenUsernameDoesNotExist_createsUserSuccessfully() {
        UserDto dto = UserDto.builder()
                .username("new_user")
                .fullName("New User")
                .role("ROLE_USER")
                .password("123456")
                .userGroupId("CIF")
                .build();

        when(passwordEncoder.encode("123456")).thenReturn("encoded_pass");

        User savedUser = User.builder()
                .id(100L)
                .username("new_user")
                .fullName("New User")
                .role("ROLE_USER")
                .userGroup(mockGroup)
                .build();

        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);

        UserDto result = adminService.createUser(dto);

        verify(userRepository, times(1)).saveAndFlush(any(User.class));
        assertEquals("new_user", result.getUsername());
    }

    @Test
    void createUser_whenEmployeeIdExists_throwsDataIntegrityViolationException() {
        UserDto dto = UserDto.builder()
                .username("new_user")
                .fullName("New User")
                .role("ROLE_USER")
                .userGroupId("CIF")
                .employeeId("123456")
                .build();

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_users_employee_id constraint violation"));

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> adminService.createUser(dto)
        );
        verify(userRepository, times(1)).saveAndFlush(any(User.class));
    }

    @Test
    void createUser_whenEmailExists_throwsDataIntegrityViolationException() {
        UserDto dto = UserDto.builder()
                .username("new_user")
                .fullName("New User")
                .role("ROLE_USER")
                .userGroupId("CIF")
                .email("test@bidv.com.vn")
                .build();

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_users_email constraint violation"));

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> adminService.createUser(dto)
        );
        verify(userRepository, times(1)).saveAndFlush(any(User.class));
    }
}
