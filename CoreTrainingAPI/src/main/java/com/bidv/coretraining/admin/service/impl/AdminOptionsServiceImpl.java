package com.bidv.coretraining.admin.service.impl;
import com.bidv.coretraining.access.dto.AccessManagementDto;
import com.bidv.coretraining.admin.dto.AdminOptionsDto;
import com.bidv.coretraining.training.dto.TrainingStructureDto;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.admin.service.AdminOptionsService;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.security.*;
import com.bidv.coretraining.training.repository.TrainingDomainRepository;
import com.bidv.coretraining.training.repository.TrainingProgramRepository;
import com.bidv.coretraining.user.entity.OrganizationUnit;
import com.bidv.coretraining.user.repository.OrganizationUnitRepository;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AdminOptionsServiceImpl implements AdminOptionsService {
    private final AuthorizationService auth;
    private final OrganizationUnitRepository units;
    private final TrainingProgramRepository programs;
    private final CourseRepository courses;
    private final TrainingDomainRepository domains;

    public AdminOptionsServiceImpl(AuthorizationService auth, OrganizationUnitRepository units,
            TrainingProgramRepository programs, CourseRepository courses, TrainingDomainRepository domains) {
        this.auth = auth;
        this.units = units;
        this.programs = programs;
        this.courses = courses;
        this.domains = domains;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AdminOptionsDto options(String page) {
        AccessPolicy
                .require(Set.of("users", "roles", "catalog", "courses", "exams", "documents").contains(page));
        auth.requireOperation(page, "read");
        var u = auth.current();
        String permission = page + ":read";
        boolean canManageDomains = AccessPolicy.has(u, "catalog:create");
        List<AccessManagementDto.UnitDto> unitOptions = units.findAll().stream()
                .filter(n -> AccessPolicy.system(u) || AccessPolicy.inScope(u, n))
                .map(n -> new AccessManagementDto.UnitDto(n.getId(), n.getName(), n.getType(),
                        n.getParent() == null ? null : n.getParent().getId(), n.isActive()))
                .toList();
        // These pages need the organization tree only. Modal course choices use
        // their own bounded read; training/document option contracts stay below.
        if (Set.of("users", "roles").contains(page)) {
            return new AdminOptionsDto(canManageDomains, unitOptions, List.of(), List.of(), List.of());
        }

        var courseOptions = courses.findAll().stream().filter(c -> AccessPolicy.allowsCourse(u, permission, c))
                .map(c -> {
                    UnitGrants grants = UnitGrants.of(c.getUnitGrants(), Set.of());
                    return new AdminOptionsDto.CourseOption(c.getId(), c.getName(),
                            c.getCode() == null ? "" : c.getCode(), grants.mandatory(), grants.optional(),
                            grants.all());
                })
                .toList();
        var domainOptions = domains.findAll().stream()
                .map(d -> {
                    UnitGrants grants = UnitGrants.of(Map.of(), unitIds(d.getUnits()));
                    return new TrainingStructureDto.DomainDto(d.getId(), d.getCode(), d.getName(),
                            d.getDescription(), d.getStatus(), Set.of("read"), d.getBusinessModule(),
                            d.getSeqNumber(), grants.all(), grants.mandatory(), grants.optional());
                })
                .toList();
        var programOptions = programs.findAll().stream().filter(p -> AccessPolicy.allowsProgram(u, permission, p))
                .map(p -> {
                    UnitGrants grants = UnitGrants.of(Map.of(), unitIds(p.getUnits()));
                    return new TrainingStructureDto.ProgramDto(p.getId(), p.getDomain() == null ? null : p.getDomain().getId(),
                            p.getCode(), p.getName(), p.getDescription(), p.getStatus(), grants.all(),
                            Stream.of("read", "create", "update", "delete")
                                    .filter(action -> AccessPolicy.allowsProgram(u, page + ":" + action, p))
                                    .collect(Collectors.toSet()),
                            p.getBusinessModule(), p.getLevel(), grants.mandatory(), grants.optional(), false,
                            p.getSeqNumber());
                })
                .toList();
        return new AdminOptionsDto(canManageDomains, unitOptions, courseOptions, domainOptions,
                programOptions);
    }

    private static Set<String> unitIds(Collection<OrganizationUnit> assigned) {
        return assigned.stream().map(OrganizationUnit::getId).collect(Collectors.toSet());
    }

    /** Splits a unit-to-grant map into mandatory/optional ids; {@code all} also includes directly assigned units. */
    private record UnitGrants(Set<String> mandatory, Set<String> optional, Set<String> all) {
        static UnitGrants of(Map<String, String> unitGrants, Set<String> assigned) {
            Set<String> mandatory = new HashSet<>();
            Set<String> optional = new HashSet<>();
            if (unitGrants != null) {
                for (var entry : unitGrants.entrySet()) {
                    if ("MANDATORY".equalsIgnoreCase(entry.getValue()))
                        mandatory.add(entry.getKey());
                    else
                        optional.add(entry.getKey());
                }
            }
            Set<String> all = new HashSet<>(assigned);
            all.addAll(mandatory);
            all.addAll(optional);
            return new UnitGrants(mandatory, optional, all);
        }
    }
}
