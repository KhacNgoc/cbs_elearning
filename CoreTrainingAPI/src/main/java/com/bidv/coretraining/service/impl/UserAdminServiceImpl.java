package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.service.PermissionCache;
import com.bidv.coretraining.service.AuthorizationService;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.Iterator;
import java.util.stream.Collectors;
import com.bidv.coretraining.service.UserAdminService;

@Service
public class UserAdminServiceImpl implements UserAdminService {
    private final AuthorizationService authorization;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCache permissionCache;

    public UserAdminServiceImpl(AuthorizationService authorization, UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PermissionCache permissionCache) {
        this.authorization = authorization;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionCache = permissionCache;
    }

    private UserDto mapToUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .allowedActions(java.util.stream.Stream.of("create", "read", "update", "delete")
                        .filter(a -> AccessPolicy.allows(authorization.current(),
                                "users:" + a, user.getOrganizationUnit()))
                        .collect(java.util.stream.Collectors.toSet()))
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatarColor(user.getAvatarColor())
                .role(user.getRole()).roles(user.effectiveRoles())
                .roleNames(AccessPolicy.roleNames(user))
                .permissions(AccessPolicy.permissions(user))
                .unitId(user.getOrganizationUnit() == null ? null : user.getOrganizationUnit().getId())
                .scopeUnitIds(user.getScopeUnitIds()).status(user.getStatus()).learnerType(user.getLearnerType())
                .points(user.getPoints())
                .employeeId(user.getEmployeeId())
                .email(user.getEmail())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return authorization.manageableUsers().stream()
                .map(this::mapToUserDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserDto createUser(UserDto userDto) {
        authorization.permission("USER_MANAGE");
        String rawPassword = userDto.getPassword() != null && !userDto.getPassword().isBlank()
                ? userDto.getPassword()
                : java.util.UUID.randomUUID().toString();
        if (rawPassword.length() < 8)
            throw new BadRequestException("Mật khẩu phải có tối thiểu 8 ký tự");
        User user = User.builder()
                .username(userDto.getUsername())
                .password(passwordEncoder.encode(rawPassword))
                .fullName(userDto.getFullName())
                .avatarColor(userDto.getAvatarColor() != null && !userDto.getAvatarColor().isBlank()
                        ? userDto.getAvatarColor()
                        : "#005B41")
                .role(userDto.getRole() != null && !userDto.getRole().isBlank()
                        ? userDto.getRole()
                        : "ROLE_USER")
                .points(userDto.getPoints() != null ? userDto.getPoints() : 0)
                .employeeId(userDto.getEmployeeId())
                .email(userDto.getEmail())
                .mustChangePassword(true)
                .build();
        applyAccess(user, userDto);
        User saved = userRepository.save(user);
        return mapToUserDto(saved);
    }

    @Override
    @Transactional
    public UserDto updateUser(Long id, UserDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));
        authorization.user(user);
        user.setFullName(userDto.getFullName());
        user.setAvatarColor(userDto.getAvatarColor());

        user.setPoints(userDto.getPoints());
        user.setEmployeeId(userDto.getEmployeeId());
        user.setEmail(userDto.getEmail());
        if (userDto.getPassword() != null && !userDto.getPassword().trim().isEmpty()) {
            if (userDto.getPassword().length() < 8)
                throw new BadRequestException("Mật khẩu phải có tối thiểu 8 ký tự");
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
            user.setMustChangePassword(true);
        }
        applyAccess(user, userDto);
        User saved = userRepository.save(user);
        return mapToUserDto(saved);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));
        authorization.user(user);
        AccessPolicy.require(!user.getId().equals(authorization.current().getId()));
        userRepository.delete(user);
        permissionCache.invalidateAfterCommit();
    }

    @Override
    @Transactional
    public void importUsersFromExcel(MultipartFile file) {
        // Legacy import has no unit column; restrict to global admin to avoid unscoped
        // accounts.
        authorization.permission("ORG_MANAGE");
        try (InputStream input = file.getInputStream(); Workbook workbook = new XSSFWorkbook(input)) {
            Iterator<Row> rows = workbook.getSheetAt(0).iterator();
            if (rows.hasNext())
                rows.next();
            while (rows.hasNext()) {
                Row row = rows.next();
                String employeeId = cellValue(row.getCell(1)).trim();
                String email = cellValue(row.getCell(2)).trim();
                String fullName = cellValue(row.getCell(3)).trim();
                if (employeeId.isEmpty() || email.isEmpty() || fullName.isEmpty()
                        || userRepository.existsByUsername(email))
                    continue;

                String[] colors = { "#005B41", "#4F46E5", "#10B981", "#F59E0B", "#EF4444" };
                userRepository.save(User.builder()
                        .username(email).email(email).employeeId(employeeId).fullName(fullName)
                        .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                        .role("UNASSIGNED").dynamicAccessInitialized(true).points(0)
                        .avatarColor(colors[Math.abs(employeeId.hashCode()) % colors.length])
                        .mustChangePassword(true)
                        .build());
            }
        } catch (Exception exception) {
            throw new RuntimeException("Lỗi khi import dữ liệu Excel: " + exception.getMessage(), exception);
        }
    }

    private void applyAccess(User user, UserDto dto) {
        User actor = authorization.current();
        boolean system = AccessPolicy.system(actor);
        if (user.getId() != null && dto.getRoles() != null)
            AccessPolicy
                    .require(new java.util.HashSet<>(dto.getRoles()).equals(user.effectiveRoles()));
        OrganizationUnit unit = dto.getUnitId() == null || dto.getUnitId().isBlank() ? null
                : authorization.unit(dto.getUnitId());
        if (!system)
            authorization.scope(unit);
        if (unit != null && !AccessPolicy.activeUnit(unit))
            throw new BadRequestException("Đơn vị đã ngừng hoạt động");
        java.util.Set<String> scopes = user.getScopeUnitIds();
        if (dto.getScopeUnitIds() != null)
            AccessPolicy
                    .require(new java.util.HashSet<>(dto.getScopeUnitIds()).equals(scopes));
        if (user.getId() != null && dto.getRole() != null)
            AccessPolicy.require(dto.getRole().equals(user.getRole()));
        if (!system && user.getId() != null && user.getId().equals(actor.getId()))
            AccessPolicy
                    .require(java.util.Objects.equals(unit == null ? null : unit.getId(),
                            actor.getOrganizationUnit() == null ? null : actor.getOrganizationUnit().getId()));
        String status = dto.getStatus() == null ? user.getStatus() : dto.getStatus();
        if (user.getId() != null && java.util.Objects.equals(user.getId(), actor.getId()))
            AccessPolicy.require("ACTIVE".equals(status));
        if (AccessPolicy.system(user))
            AccessPolicy.require("ACTIVE".equals(status));
        if (!java.util.Set.of("ACTIVE", "LOCKED", "DISABLED").contains(status))
            throw new BadRequestException("Trạng thái tài khoản không hợp lệ");
        String learnerType = dto.getLearnerType() == null
                ? (user.getLearnerType() == null ? "CURRENT_STAFF" : user.getLearnerType())
                : dto.getLearnerType();
        if (!java.util.Set.of("NEW_STAFF", "TRANSFERRED_STAFF", "CURRENT_STAFF", "EXTERNAL").contains(learnerType))
            throw new BadRequestException("Đối tượng học viên không hợp lệ");
        if (user.getId() != null)
            user.setTokenVersion(user.getTokenVersion() + 1);
        if (!user.isDynamicAccessInitialized()) {
            user.setDynamicAccessInitialized(true);
            user.setAssignedRoles(new java.util.HashSet<>());
            user.setRoles(new java.util.HashSet<>());
            user.setRole("UNASSIGNED");
        }
        permissionCache.invalidateAfterCommit();
        user.setOrganizationUnit(unit);
        user.setScopeUnitIds(new java.util.HashSet<>(scopes));
        user.setStatus(status);
        user.setLearnerType(learnerType);
    }

    private String cellValue(Cell cell) {
        if (cell == null)
            return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            return value == (long) value ? String.valueOf((long) value) : String.valueOf(value);
        }
        return cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : cell.toString();
    }

    @Override
    public byte[] generateExcelTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Import Template");
            Row header = sheet.createRow(0);
            String[] labels = { "STT", "Mã nhân viên (*)", "User email (*)", "Tên nhân viên (*)" };
            for (int i = 0; i < labels.length; i++)
                header.createCell(i).setCellValue(labels[i]);
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue(1);
            sample.createCell(1).setCellValue("NV0982");
            sample.createCell(2).setCellValue("nguyenvanan@bidv.com.vn");
            sample.createCell(3).setCellValue("Nguyễn Văn An");
            for (int i = 0; i < labels.length; i++)
                sheet.autoSizeColumn(i);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new RuntimeException("Lỗi khi tạo file Excel mẫu: " + exception.getMessage(), exception);
        }
    }
}
