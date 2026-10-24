package com.bidv.coretraining.controller;

import com.bidv.coretraining.entity.OrganizationUnit;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.*;
import com.bidv.coretraining.security.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/admin/options")
public class AdminOptionsController {
    private final AuthorizationService auth;
    private final OrganizationUnitRepository units;
    private final TrainingProgramRepository programs;
    private final CourseRepository courses;
    private final TrainingDomainRepository domains;
    @org.springframework.beans.factory.annotation.Autowired
    private UserGroupRepository groups;

    public AdminOptionsController(AuthorizationService auth, OrganizationUnitRepository units,
            TrainingProgramRepository programs, CourseRepository courses, TrainingDomainRepository domains) {
        this.auth = auth;
        this.units = units;
        this.programs = programs;
        this.courses = courses;
        this.domains = domains;
    }

    @GetMapping("/{page}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> options(@PathVariable String page) {
        AccessPolicy
                .require(Set.of("users", "groups", "roles", "catalog", "courses", "exams", "documents").contains(page));
        auth.requireOperation(page, "read");
        var u = auth.current();
        String permission = page + ":read";
        return Map.of(
                "canManageDomains", AccessPolicy.has(u, "catalog:create"), "units",
                units.findAll().stream().filter(n -> AccessPolicy.system(u) || AccessPolicy.inScope(u, n))
                        .map(n -> new AccessManagementService.UnitDto(n.getId(), n.getName(), n.getType(),
                                n.getParent() == null ? null : n.getParent().getId(), n.isActive()))
                        .toList(),
                "groups",
                groups.findAll().stream().filter(g -> AccessPolicy.allows(u, permission, g.getOrganizationUnit()))
                        .map(g -> new AccessManagementService.GroupDto(g.getId(), g.getName(),
                                g.getOrganizationUnit() == null ? null : g.getOrganizationUnit().getId(),
                                g.getMandatoryCourses().stream().map(com.bidv.coretraining.entity.Course::getId)
                                        .collect(Collectors.toSet())))
                        .toList(),
                "courses",
                courses.findAll().stream().filter(c -> AccessPolicy.allowsCourse(u, permission, c))
                        .map(c -> {
                            Set<String> mandatoryUnits = new HashSet<>();
                            Set<String> optionalUnits = new HashSet<>();
                            if (c.getUnitGrants() != null) {
                                for (var entry : c.getUnitGrants().entrySet()) {
                                    if ("MANDATORY".equalsIgnoreCase(entry.getValue())) {
                                        mandatoryUnits.add(entry.getKey());
                                    } else {
                                        optionalUnits.add(entry.getKey());
                                    }
                                }
                            }
                            Set<String> allUnits = new HashSet<>(mandatoryUnits);
                            allUnits.addAll(optionalUnits);
                            Map<String, Object> map = new HashMap<>();
                            map.put("id", c.getId());
                            map.put("name", c.getName());
                            map.put("code", c.getCode() == null ? "" : c.getCode());
                            map.put("mandatoryUnitIds", mandatoryUnits);
                            map.put("optionalUnitIds", optionalUnits);
                            map.put("unitIds", allUnits);
                            return map;
                        }).toList(),
                "domains",
                domains.findAll().stream()
                        .map(d -> {
                            Set<String> mandatoryUnits = new HashSet<>();
                            Set<String> optionalUnits = new HashSet<>();
                            if (d.getUnitGrants() != null) {
                                for (var entry : d.getUnitGrants().entrySet()) {
                                    if ("MANDATORY".equalsIgnoreCase(entry.getValue())) {
                                        mandatoryUnits.add(entry.getKey());
                                    } else {
                                        optionalUnits.add(entry.getKey());
                                    }
                                }
                            }
                            Set<String> allUnits = new HashSet<>(d.getUnits().stream().map(OrganizationUnit::getId).toList());
                            allUnits.addAll(mandatoryUnits);
                            allUnits.addAll(optionalUnits);
                            return new TrainingStructureService.DomainDto(d.getId(), d.getCode(), d.getName(),
                                    d.getDescription(), d.getStatus(), Set.of("read"), d.getBusinessModule(),
                                    d.getSeqNumber(), allUnits, mandatoryUnits, optionalUnits);
                        })
                        .toList(),
                "programs",
                programs.findAll().stream().filter(p -> AccessPolicy.allowsProgram(u, permission, p))
                        .map(p -> {
                            Set<String> mandatoryUnits = new HashSet<>();
                            Set<String> optionalUnits = new HashSet<>();
                            if (p.getUnitGrants() != null) {
                                for (var entry : p.getUnitGrants().entrySet()) {
                                    if ("MANDATORY".equalsIgnoreCase(entry.getValue())) {
                                        mandatoryUnits.add(entry.getKey());
                                    } else {
                                        optionalUnits.add(entry.getKey());
                                    }
                                }
                            }
                            Set<String> allUnits = new HashSet<>(p.getUnits().stream().map(OrganizationUnit::getId).toList());
                            allUnits.addAll(mandatoryUnits);
                            allUnits.addAll(optionalUnits);
                            return new TrainingStructureService.ProgramDto(p.getId(), p.getDomain() == null ? null : p.getDomain().getId(),
                                    p.getCode(), p.getName(), p.getDescription(), p.getStatus(), allUnits,
                                    Stream.of("read", "create", "update", "delete")
                                            .filter(action -> AccessPolicy.allowsProgram(u, page + ":" + action, p))
                                            .collect(Collectors.toSet()),
                                    p.getBusinessModule(), p.getLevel(), mandatoryUnits, optionalUnits, false, p.getSeqNumber());
                        })
                        .toList());
    }
}
