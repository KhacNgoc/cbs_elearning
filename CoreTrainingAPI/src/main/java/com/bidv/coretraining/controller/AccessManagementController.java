package com.bidv.coretraining.controller;

import com.bidv.coretraining.service.AccessManagementService;
import com.bidv.coretraining.dto.AccessManagementDto.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AccessManagementController {
    private final AccessManagementService service;

    public AccessManagementController(AccessManagementService service) {
        this.service = service;
    }

    @GetMapping("/units")
    public List<UnitDto> units() {
        return service.units();
    }

    @PostMapping("/units")
    public UnitDto create(@RequestBody UnitDto r) {
        return service.saveUnit(null, r);
    }

    @PutMapping("/units/{id}")
    public UnitDto update(@PathVariable String id, @RequestBody UnitDto r) {
        return service.saveUnit(id, r);
    }

    @GetMapping("/access/groups")
    public List<GroupDto> groups() {
        return service.groups();
    }

    @PostMapping("/groups")
    public GroupDto create(@RequestBody GroupDto r) {
        return service.saveGroup(null, r);
    }

    @PutMapping("/groups/{id}")
    public GroupDto update(@PathVariable String id, @RequestBody GroupDto r) {
        return service.saveGroup(id, r);
    }

    @DeleteMapping("/groups/{id}")
    public void delete(@PathVariable String id) {
        service.deleteGroup(id);
    }

    @GetMapping("/groups/{id}/members")
    public GroupMembersPage members(@PathVariable String id,
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "false") boolean membersOnly) {
        return service.members(id, query, page, size, membersOnly);
    }

    @PutMapping("/groups/{id}/members")
    public void updateMembers(@PathVariable String id, @RequestBody MembershipChanges changes) {
        service.updateMembers(id, changes);
    }

    @GetMapping("/courses/{id}/access")
    public CourseAccessDto access(@PathVariable String id) {
        return service.courseAccess(id);
    }

    @PutMapping("/courses/{id}/access")
    public CourseAccessDto access(@PathVariable String id, @RequestBody CourseAccessDto r) {
        return service.saveCourseAccess(id, r);
    }
}
