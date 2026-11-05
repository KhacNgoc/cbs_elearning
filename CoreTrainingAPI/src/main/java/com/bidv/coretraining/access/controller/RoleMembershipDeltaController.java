package com.bidv.coretraining.access.controller;
import com.bidv.coretraining.access.dto.RoleMembershipDeltaDto;
import com.bidv.coretraining.access.service.RoleMembershipDeltaService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/rbac/roles")
public class RoleMembershipDeltaController {
    private final RoleMembershipDeltaService service;
    public RoleMembershipDeltaController(RoleMembershipDeltaService service) { this.service = service; }
    @PutMapping("/{id}/member-changes")
    public void apply(@PathVariable String id, @RequestBody RoleMembershipDeltaDto.Changes changes) { service.apply(id, changes); }
}
