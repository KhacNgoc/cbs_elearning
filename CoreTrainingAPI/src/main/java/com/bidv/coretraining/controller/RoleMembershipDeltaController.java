package com.bidv.coretraining.controller;
import com.bidv.coretraining.service.RoleMembershipDeltaService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/rbac/roles")
public class RoleMembershipDeltaController {
    private final RoleMembershipDeltaService service;
    public RoleMembershipDeltaController(RoleMembershipDeltaService service) { this.service = service; }
    @PutMapping("/{id}/member-changes")
    public void apply(@PathVariable String id, @RequestBody RoleMembershipDeltaService.Changes changes) { service.apply(id, changes); }
}
