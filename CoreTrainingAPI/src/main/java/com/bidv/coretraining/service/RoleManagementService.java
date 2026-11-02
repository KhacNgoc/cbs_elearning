package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.AccessManagementDto;

import com.bidv.coretraining.dto.RoleManagementDto.*;
import java.util.*;

/** Business operations for RoleManagementService. */
public interface RoleManagementService {
    public List<RoleDto> list();

    public List<MemberDto> members();

    public RoleDto save(String id, RoleRequest request);

    public RoleDto grants(String id, GrantRequest request);

    public RoleDto assign(String id, MembersRequest request);

    public void delete(String id);

    public List<PageDto> pages();

    public List<AccessManagementDto.UnitDto> units();
}
