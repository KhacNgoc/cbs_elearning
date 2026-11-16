package com.bidv.coretraining.admin.controller;

import com.bidv.coretraining.admin.service.AdminOptionsService;
import org.springframework.web.bind.annotation.*;
import com.bidv.coretraining.admin.dto.AdminOptionsDto;

@RestController
@RequestMapping("/api/admin/options")
public class AdminOptionsController {
    private final AdminOptionsService service;

    public AdminOptionsController(AdminOptionsService service) {
        this.service = service;
    }

    @GetMapping("/{page}")
    public AdminOptionsDto options(@PathVariable String page,
            @RequestParam(required = false) java.util.Set<String> parts) {
        return service.options(page, parts);
    }
}
