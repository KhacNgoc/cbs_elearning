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

    public record BatchCourseAllocationRequest(String unitId, String grantType, String programId, boolean allInProgram,
            List<String> courseIds) {
    }

    public record UnitAllocationAssignRequest(String unitId, String categoryType, String grantType, String domainId,
            String programId, boolean allInProgram, List<String> courseIds) {
    }

    public record UnitAllocationToggleRequest(String unitId, String categoryType, String targetId, String grantType) {
    }
}
