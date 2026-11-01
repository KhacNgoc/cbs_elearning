package com.bidv.coretraining.controller;

import com.bidv.coretraining.service.AdminOptionsService;
import org.springframework.web.bind.annotation.*;
import com.bidv.coretraining.dto.AdminOptionsDto;

@RestController
@RequestMapping("/api/admin/options")
public class AdminOptionsController {
    private final AdminOptionsService service;

    public AdminOptionsController(AdminOptionsService service) {
        this.service = service;
    }

    @GetMapping("/{page}")
    public AdminOptionsDto options(@PathVariable String page) {
        return service.options(page);
    }
}
