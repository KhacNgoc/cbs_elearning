package com.bidv.coretraining.service.impl;
import com.bidv.coretraining.dto.RoleManagementDto;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.dto.RoleMembershipDeltaDto.*;

import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Applies a bounded edit set through the existing role assignment validation. */
@Service
@Transactional
public class RoleMembershipDeltaServiceImpl implements RoleMembershipDeltaService {

    private final UserRepository users;
    private final RoleManagementService roles;
    public RoleMembershipDeltaServiceImpl(UserRepository users, RoleManagementService roles) { this.users = users; this.roles = roles; }
    public void apply(String id, Changes changes) {
        var add = changes.addUserIds() == null ? Set.<Long>of() : changes.addUserIds();
        var remove = changes.removeUserIds() == null ? Set.<Long>of() : changes.removeUserIds();
        if (changes.version() == null || add.stream().anyMatch(Objects::isNull) || remove.stream().anyMatch(Objects::isNull) || !Collections.disjoint(add, remove) || add.size() + remove.size() > 1000)
            throw new BadRequestException("Thay đổi thành viên không hợp lệ");
        var desired = new HashSet<>(users.findIdsByAssignedRole(id)); desired.addAll(add); desired.removeAll(remove);
        roles.assign(id, new RoleManagementDto.MembersRequest(desired, changes.version()));
    }
}
