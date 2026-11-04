package com.bidv.coretraining.repository;

import com.bidv.coretraining.dto.CourseDto;
import com.bidv.coretraining.dto.PagedResult;
import com.bidv.coretraining.dto.TrainingStructureDto.DomainListItem;
import com.bidv.coretraining.dto.TrainingStructureDto.ProgramListItem;
import com.bidv.coretraining.dto.TrainingStructureDto.UnitAllocationDirectoryDto;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Native queries behind unit allocation and catalog soft deletes. */
public interface UnitAllocationNativeRepository {
    /** Registers active staff of the units (and their sub-units) to the courses; existing rows are kept. */
    int enrollStaffForCourses(Collection<String> courseIds, Collection<String> unitIds);

    /** Removes registrations that have no completed lesson yet. */
    int unenrollUnstartedStaff(Collection<String> courseIds);

    /** Same as {@link #unenrollUnstartedStaff(Collection)}, limited to staff of the units and their sub-units. */
    int unenrollUnstartedStaff(Collection<String> courseIds, Collection<String> unitIds);

    int countActiveStaffInUnits(Collection<String> unitIds);

    List<String> courseIdsInDomain(String domainId);

    List<String> courseIdsInProgram(String programId);

    void softDeleteDomainTree(String domainId, String suffix, int negBase);

    void softDeleteProgramTree(String programId, String suffix, int negBase);

    void softDeleteCourseTree(String courseId, String suffix, int negBase);

    UnitAllocationDirectoryDto unitAllocationDirectory(String search, String type, String status, int page, int size);

    List<Map<String, Object>> getUnitAllocatedItems(String unitId);

    PagedResult<DomainListItem> adminDomainsPagedUnallocated(String search, int page, int size, String unitId);

    PagedResult<ProgramListItem> adminProgramsPagedUnallocated(String domainId, String search, int page, int size, String unitId);

    PagedResult<CourseDto> adminCoursesByProgramPagedUnallocated(String programId, String search, int page, int size, String unitId);
}
