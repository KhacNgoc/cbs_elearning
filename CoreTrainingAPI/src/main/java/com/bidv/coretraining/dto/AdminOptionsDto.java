package com.bidv.coretraining.dto;

import java.util.List;
import java.util.Set;

/** Option lists used by admin page forms and filters. */
public record AdminOptionsDto(boolean canManageDomains, List<AccessManagementDto.UnitDto> units,
        List<CourseOption> courses,
        List<TrainingStructureDto.DomainDto> domains, List<TrainingStructureDto.ProgramDto> programs) {

    public record CourseOption(String id, String name, String code, Set<String> mandatoryUnitIds,
            Set<String> optionalUnitIds, Set<String> unitIds) {
    }
}
