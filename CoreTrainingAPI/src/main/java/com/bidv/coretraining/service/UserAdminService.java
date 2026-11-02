package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.*;

import java.util.List;

public interface UserAdminService {
    List<UserDto> getAllUsers();

    UserDto createUser(UserDto userDto);

    UserDto updateUser(Long id, UserDto userDto);

    void deleteUser(Long id);

    void importUsersFromExcel(org.springframework.web.multipart.MultipartFile file);

    List<UserGroupDto> getAllUserGroups();

    byte[] generateExcelTemplate();
}
