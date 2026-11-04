package com.bidv.coretraining.dto;

import com.bidv.coretraining.dto.*;
import java.util.*;

/** Data contracts for TrainingStructureService. */
public final class TrainingStructureDto {
    private TrainingStructureDto() {}

    public record DomainDto(String id, String code, String name, String description, String status,
            Set<String> allowedActions, String businessModule, Integer seqNumber,
            Set<String> unitIds, Set<String> mandatoryUnitIds, Set<String> optionalUnitIds) {
        public DomainDto(String id, String code, String name, String description, String status) {
            this(id, code, name, description, status, Set.of(), null, null, Set.of(), Set.of(), Set.of());
        }
        public DomainDto(String id, String code, String name, String description, String status, Set<String> allowedActions) {
            this(id, code, name, description, status, allowedActions, null, null, Set.of(), Set.of(), Set.of());
        }
        public DomainDto(String id, String code, String name, String description, String status, Set<String> allowedActions,
                String businessModule, Integer seqNumber) {
            this(id, code, name, description, status, allowedActions, businessModule, seqNumber, Set.of(), Set.of(), Set.of());
        }
    }

    public record ProgramDto(
            String id,
            String domainId,
            String code,
            String name,
            String description,
            String status,
            Set<String> unitIds,
            Set<String> allowedActions,
            String businessModule,
            String level,
            Set<String> mandatoryUnitIds,
            Set<String> optionalUnitIds,
            Boolean autoEnrollStaff,
            Integer seqNumber) {

        public ProgramDto(String id, String domainId, String code, String name, String description, String status,
                Set<String> unitIds, Set<String> allowedActions) {
            this(id, domainId, code, name, description, status, unitIds, allowedActions, null, null,
                    java.util.Collections.emptySet(), java.util.Collections.emptySet(), false, null);
        }
    }

    public record ChapterDto(String id, String courseId, String code, String title, String description,
            Integer chapterOrder, String status, Long version, List<LessonDto> lessons, boolean locked,
            boolean completed) {
    }

    public record OutlineDto(CourseDto course, List<ChapterDto> chapters, boolean completed, String resumeLessonId,
            LearnerWorkspaceDto.Resume resume) {
    }

    public record CourseRequest(String code, String name, String programId, String description, String iconName,
            Long version,
            String thumbnail, String businessModule, String level, String keywords,
            java.time.LocalDate completionDeadline,
            Integer seqNumber,
            Set<String> mandatoryUnitIds,
            Set<String> optionalUnitIds,
            Set<String> unitIds) {
        public CourseRequest(String code, String name, String programId, String description, String iconName,
                Long version,
                String thumbnail, String businessModule, String level, String keywords,
                java.time.LocalDate completionDeadline,
                Integer seqNumber) {
            this(code, name, programId, description, iconName, version, thumbnail, businessModule, level, keywords,
                    completionDeadline, seqNumber, null, null, null);
        }
        public CourseRequest(String code, String name, String programId, String description, String iconName,
                Long version,
                String thumbnail, String businessModule, String level, String keywords,
                java.time.LocalDate completionDeadline) {
            this(code, name, programId, description, iconName, version, thumbnail, businessModule, level, keywords,
                    completionDeadline, null, null, null, null);
        }
        public CourseRequest(String code, String name, String programId, String description, String iconName,
                Long version,
                String thumbnail, String businessModule, String level, String keywords,
                java.time.LocalDate completionDeadline,
                Set<String> mandatoryUnitIds,
                Set<String> optionalUnitIds,
                Set<String> unitIds) {
            this(code, name, programId, description, iconName, version, thumbnail, businessModule, level, keywords,
                    completionDeadline, null, mandatoryUnitIds, optionalUnitIds, unitIds);
        }
    }

    public record ChapterRequest(String code, String title, String description, Long version) {
    }

    public record CourseStatusRequest(String status) {
    }

    public record CatalogDto(List<DomainDto> domains, List<ProgramDto> programs) {
    }

    public record NextCodeDto(String code, int seq, String prefix) {
    }

    public record UnitAllocationResultDto(String unitId, String unitName, String categoryType, String grantType,
            int allocatedCoursesCount, int unitUsersCount, int enrolledUsersCount) {
    }

    public record UnitAllocationRemovalDto(String unitId, int removedCoursesCount) {
    }

    /** Domain row of the admin domain list, with its live program count. */
    public record DomainListItem(String id, String code, String name, String description, String status,
            Set<String> allowedActions, String businessModule, Integer seqNumber, Set<String> unitIds,
            Set<String> mandatoryUnitIds, Set<String> optionalUnitIds, long programsCount) {
        public static DomainListItem of(DomainDto d, long programsCount) {
            return new DomainListItem(d.id(), d.code(), d.name(), d.description(), d.status(), d.allowedActions(),
                    d.businessModule(), d.seqNumber(), d.unitIds(), d.mandatoryUnitIds(), d.optionalUnitIds(),
                    programsCount);
        }
    }

    /** Program row of the admin program list, with its live course count. */
    public record ProgramListItem(String id, String domainId, String code, String name, String description,
            String status, Set<String> unitIds, Set<String> allowedActions, String businessModule, String level,
            Set<String> mandatoryUnitIds, Set<String> optionalUnitIds, Boolean autoEnrollStaff, Integer seqNumber,
            long coursesCount) {
        public static ProgramListItem of(ProgramDto p, long coursesCount) {
            return new ProgramListItem(p.id(), p.domainId(), p.code(), p.name(), p.description(), p.status(),
                    p.unitIds(), p.allowedActions(), p.businessModule(), p.level(), p.mandatoryUnitIds(),
                    p.optionalUnitIds(), p.autoEnrollStaff(), p.seqNumber(), coursesCount);
        }
    }

    public record StaffCountDto(long count) {
    }

    public record BatchAllocationItem(
            String categoryType,
            String targetId,
            String grantType,
            Boolean onlyUnallocated,
            List<String> excludeIds) {
        public BatchAllocationItem(String categoryType, String targetId, String grantType) {
            this(categoryType, targetId, grantType, Boolean.TRUE, java.util.Collections.emptyList());
        }
    }

    }

    public record UnitBatchAllocationRequest(String unitId, List<BatchAllocationItem> items) {
    }

    public record BatchChangeItem(String action, String categoryType, String targetId, String grantType) {
    }

    public record UnitBatchSyncRequest(String unitId, List<BatchChangeItem> changes) {
    }

    public record BatchAllocationResultDto(String unitId, String unitName, int domainsCount, int programsCount,
            int coursesCount, int totalAffectedCourses, int unitUsersCount, int enrolledUsersCount, String status) {
    }

    public record BatchSyncResultDto(String unitId, String unitName, Integer updatedCount, Integer removedCount,
            Integer totalChanges, String status, String message) {
        public static BatchSyncResultDto noChanges(String message) {
            return new BatchSyncResultDto(null, null, null, null, null, "SUCCESS", message);
        }
    }

    public record UnitAllocationDirectoryDto(List<Item> items, int page, int size, int totalElements, int totalPages,
            Stats stats) {
        public record Item(String id, String name, String type, boolean active, String parentId, int domainCount,
                int programCount, int courseCount,
                @com.fasterxml.jackson.annotation.JsonProperty("isAllocated") boolean isAllocated) {
        }

        public record Stats(int totalCount, int allocatedCount, int unallocatedCount, int branchCount,
                int departmentCount) {
        }
    }

    public record BatchCourseAllocationRequest(String unitId, String grantType, String programId, boolean allInProgram,
            List<String> courseIds) {
    }

    public record UnitAllocationAssignRequest(String unitId, String categoryType, String grantType, String domainId,
            String programId, boolean allInProgram, List<String> courseIds) {
    }

    public record UnitAllocationToggleRequest(String unitId, String categoryType, String targetId, String grantType) {
    }
}
