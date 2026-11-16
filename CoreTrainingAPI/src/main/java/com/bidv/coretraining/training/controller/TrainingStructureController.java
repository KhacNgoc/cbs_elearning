package com.bidv.coretraining.training.controller;

import com.bidv.coretraining.course.dto.CourseDto;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.training.dto.PagedResult;
import com.bidv.coretraining.training.dto.TrainingStructureDto.*;
import com.bidv.coretraining.training.service.TrainingStructureService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TrainingStructureController {
    private final TrainingStructureService service;

    public TrainingStructureController(TrainingStructureService service) {
        this.service = service;
    }

    @GetMapping("/admin/training/catalog")
    public CatalogDto catalog() {
        return service.catalog();
    }

    @GetMapping("/admin/catalog/units/staff-count")
    public StaffCountDto countActiveStaffInUnits(
            @RequestParam(name = "unitIds", required = false) Set<String> unitIds) {
        long count = (unitIds == null || unitIds.isEmpty())
                ? 0L
                : service.countActiveStaffInUnitsNative(unitIds);
        return new StaffCountDto(count);
    }

    @GetMapping("/admin/training/next-code")
    public NextCodeDto nextCode(
            @RequestParam(defaultValue = "program") String type,
            @RequestParam String module) {
        return service.getNextCode(type, module);
    }

    @PostMapping("/admin/training-domains")
    public DomainDto domain(@RequestBody DomainDto r) {
        return service.saveDomain(null, r);
    }

    @PutMapping("/admin/training-domains/{id}")
    public DomainDto domain(@PathVariable String id, @RequestBody DomainDto r) {
        return service.saveDomain(id, r);
    }

    @DeleteMapping("/admin/training-domains/{id}")
    public void deleteDomain(@PathVariable String id) {
        service.deleteDomain(id);
    }

    @PostMapping("/admin/training-programs")
    public ProgramDto program(@RequestBody ProgramDto r) {
        return service.saveProgram(null, r);
    }

    @PutMapping("/admin/training-programs/{id}")
    public ProgramDto program(@PathVariable String id, @RequestBody ProgramDto r) {
        return service.saveProgram(id, r);
    }

    @DeleteMapping("/admin/training-programs/{id}")
    public void deleteProgram(@PathVariable String id) {
        service.deleteProgram(id);
    }

    @GetMapping("/admin/courses")
    public List<CourseDto> courses() {
        return service.adminCourses();
    }

    @GetMapping("/admin/unit-allocations/directory")
    public UnitAllocationDirectoryDto getUnitAllocationDirectory(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.getUnitAllocationDirectoryPaged(search, type, status, page, size);
    }

    @PostMapping("/admin/unit-allocations/batch-assign")
    public BatchAllocationResultDto batchAssignUnitAllocation(@RequestBody UnitBatchAllocationRequest request) {
        return service.batchAssignUnitAllocation(request.unitId(), request.items());
    }

    @PostMapping("/admin/unit-allocations/batch-sync")
    public BatchSyncResultDto batchSyncUnitAllocations(@RequestBody UnitBatchSyncRequest request) {
        return service.batchSyncUnitAllocations(request.unitId(), request.changes());
    }

    @PostMapping("/admin/unit-allocations/assign")
    public UnitAllocationResultDto assignUnitAllocation(@RequestBody UnitAllocationAssignRequest request) {
        return service.assignUnitAllocation(
                request.unitId(),
                request.categoryType(),
                request.grantType(),
                request.domainId(),
                request.programId(),
                request.allInProgram(),
                request.courseIds()
        );
    }

    @PatchMapping("/admin/unit-allocations/grant-type")
    public UnitAllocationResultDto toggleUnitAllocationGrantType(@RequestBody UnitAllocationToggleRequest request) {
        return service.toggleUnitAllocationGrantType(
                request.unitId(),
                request.categoryType(),
                request.targetId(),
                request.grantType()
        );
    }

    @DeleteMapping("/admin/unit-allocations")
    public UnitAllocationRemovalDto removeUnitAllocation(
            @RequestParam String unitId,
            @RequestParam String categoryType,
            @RequestParam String targetId) {
        return service.removeUnitAllocation(unitId, categoryType, targetId);
    }

    @GetMapping({"/admin/domains/paged", "/admin/domains", "/admin/training-domains/paged", "/admin/training/domains/paged"})
    public PagedResult<DomainListItem> domainsPaged(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String unallocatedForUnitId) {
        return service.adminDomainsPaged(search, page, size, unallocatedForUnitId);
    }

    @GetMapping({"/admin/programs/paged", "/admin/programs/by-domain"})
    public PagedResult<ProgramListItem> programsPaged(
            @RequestParam(required = false) String domainId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String unallocatedForUnitId) {
        return service.adminProgramsPaged(domainId, search, page, size, unallocatedForUnitId);
    }

    @GetMapping("/admin/programs/by-domains")
    public List<ProgramListItem> programsByDomains(
            @RequestParam List<String> domainIds,
            @RequestParam(required = false) String search) {
        return service.adminProgramsByDomains(domainIds, search);
    }

    @GetMapping(value = {"/admin/programs/{programId}/courses", "/admin/courses/by-program/{programId}"}, params = "page")
    public PagedResult<CourseDto> coursesByProgramPaged(
            @PathVariable String programId,
            @RequestParam int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String unallocatedForUnitId) {
        int pageSize = (size != null && size > 0) ? size : 10;
        return service.adminCoursesByProgramPaged(programId, search, page, pageSize, unallocatedForUnitId);
    }

    @GetMapping(value = {"/admin/programs/{programId}/courses", "/admin/courses/by-program/{programId}"}, params = "!page")
    public List<CourseDto> coursesByProgram(@PathVariable String programId) {
        return service.adminCoursesByProgram(programId);
    }

    @GetMapping("/admin/unit-allocations/{unitId}/allocated-items")
    public List<Map<String, Object>> getUnitAllocatedItems(@PathVariable String unitId) {
        return service.getUnitAllocatedItems(unitId);
    }

    @PostMapping("/admin/courses/batch-allocate")
    public org.springframework.http.ResponseEntity<Void> batchAllocateCourses(
            @RequestBody BatchCourseAllocationRequest request) {
        service.allocateCoursesToUnit(
                request.unitId(),
                request.grantType(),
                request.programId(),
                request.allInProgram(),
                request.courseIds()
        );
        return org.springframework.http.ResponseEntity.ok().build();
    }

    @GetMapping({"/admin/units/{unitId}/courses", "/admin/courses/by-unit/{unitId}"})
    public List<CourseDto> coursesByUnit(@PathVariable String unitId) {
        return service.adminCoursesByUnit(unitId);
    }

    @GetMapping("/admin/courses/paged")
    public PagedResult<CourseDto> coursesPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String programId) {
        return service.adminCoursesPaged(search, status, programId, page, size);
    }

    @GetMapping("/admin/courses/{id}")
    public CourseDto course(@PathVariable String id) {
        return service.adminCourse(id);
    }

    @GetMapping("/admin/courses/{id}/chapters")
    public List<ChapterDto> chapters(@PathVariable String id) {
        return service.adminChapters(id);
    }

    @GetMapping(value = "/admin/courses/{id}/chapters", params = "view=outline")
    public List<ChapterDto> chapterOutlines(@PathVariable String id) {
        return service.adminChapterOutlines(id);
    }

    @GetMapping("/admin/chapters/{id}")
    public ChapterDto chapter(@PathVariable String id) {
        return service.adminChapter(id);
    }

    @PostMapping("/admin/courses/{id}/chapters")
    public ChapterDto chapter(@PathVariable String id, @RequestBody ChapterRequest r) {
        return service.saveChapter(id, null, r);
    }

    @PutMapping("/admin/courses/{courseId}/chapters/{id}")
    public ChapterDto chapter(@PathVariable String courseId, @PathVariable String id, @RequestBody ChapterRequest r) {
        return service.saveChapter(courseId, id, r);
    }

    @DeleteMapping("/admin/chapters/{id}")
    public void deleteChapter(@PathVariable String id) {
        service.deleteChapter(id);
    }

    @PutMapping("/admin/courses/{id}/chapters/order")
    public void reorderChapters(@PathVariable String id, @RequestBody List<String> r) {
        service.reorderChapters(id, r);
    }

    @PutMapping("/admin/chapters/{id}/lessons/order")
    public void reorderLessons(@PathVariable String id, @RequestBody List<String> r) {
        service.reorderLessons(id, r);
    }

    @PostMapping("/admin/courses/{id}/status")
    public CourseDto status(@PathVariable String id, @RequestBody CourseStatusRequest r) {
        return service.status(id, r.status());
    }

    @GetMapping("/courses/{id}/outline")
    public OutlineDto outline(@PathVariable String id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.outline(id, user.getUsername());
    }
}
