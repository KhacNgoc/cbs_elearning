package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.UserDto;
import com.bidv.coretraining.dto.UserGroupDto;
import com.bidv.coretraining.service.UserAdminService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {
    private final UserAdminService service;

    public AdminUserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(service.getAllUsers());
    }

    @PostMapping("/users")
    public ResponseEntity<UserDto> createUser(@RequestBody UserDto userDto) {
        return ResponseEntity.ok(service.createUser(userDto));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @RequestBody UserDto userDto) {
        return ResponseEntity.ok(service.updateUser(id, userDto));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        service.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/upload")
    public ResponseEntity<String> uploadUsersExcel(@RequestParam("file") MultipartFile file) {
        service.importUsersFromExcel(file);
        return ResponseEntity.ok("Import thành công dữ liệu học viên!");
    }

    @GetMapping("/users/template")
    public ResponseEntity<byte[]> downloadUsersTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=user_import_template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(service.generateExcelTemplate());
    }

    @GetMapping("/groups")
    public ResponseEntity<List<UserGroupDto>> getAllUserGroups() {
        return ResponseEntity.ok(service.getAllUserGroups());
    }
}
