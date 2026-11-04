package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.AccessManagementDto;

import com.bidv.coretraining.service.RoleManagementService;
import com.bidv.coretraining.dto.RoleManagementDto.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/rbac")
public class RoleManagementController {
    private final RoleManagementService service;

    public RoleManagementController(RoleManagementService service) {
        this.service = service;
    }

    @GetMapping("/roles")
    public List<RoleDto> roles() {
        return service.list();
    }

    @GetMapping("/role-options")
    public List<RoleDto> options() {
        return service.list().stream().filter(r -> !r.learnerRole()).toList();
    }

    @GetMapping("/roles/member-options")
    public List<MemberDto> members() {
        return service.members();
    }

    @PostMapping("/roles")
    public RoleDto create(@RequestBody RoleRequest r) {
        return service.save(null, r);
    }

    @PutMapping("/roles/{id}")
    public RoleDto update(@PathVariable String id, @RequestBody RoleRequest r) {
        return service.save(id, r);
    }

    @DeleteMapping("/roles/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @PutMapping("/roles/{id}/members")
    public RoleDto members(@PathVariable String id, @RequestBody MembersRequest r) {
        return service.assign(id, r);
    }

    @PutMapping("/permissions/{id}")
    public RoleDto grants(@PathVariable String id, @RequestBody GrantRequest r) {
        return service.grants(id, r);
    }

    @GetMapping("/pages")
    public List<PageDto> pages() {
        return service.pages();
    }

    @GetMapping("/units")
    public List<AccessManagementDto.UnitDto> units() {
        return service.units();
    }
}
