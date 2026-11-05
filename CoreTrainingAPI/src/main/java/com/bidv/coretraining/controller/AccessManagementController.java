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

    @GetMapping("/courses/{id}/access")
    public CourseAccessDto access(@PathVariable String id) {
        return service.courseAccess(id);
    }

    @PutMapping("/courses/{id}/access")
    public CourseAccessDto access(@PathVariable String id, @RequestBody CourseAccessDto r) {
        return service.saveCourseAccess(id, r);
    }
}
