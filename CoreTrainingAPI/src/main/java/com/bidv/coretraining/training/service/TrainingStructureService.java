package com.bidv.coretraining.training.service;

import com.bidv.coretraining.course.dto.CourseDto;
import com.bidv.coretraining.course.entity.Chapter;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.training.dto.PagedResult;
import com.bidv.coretraining.training.dto.TrainingStructureDto.*;
import com.bidv.coretraining.training.entity.TrainingProgram;
import com.bidv.coretraining.user.entity.User;
import java.util.*;

/** Business operations for TrainingStructureService. */
public interface TrainingStructureService {
    public Course course(String id);

    public Chapter chapter(String id);

    public Lesson lesson(String id);

    public User user(String username);

    public ProgramDto programDto(TrainingProgram p);

    public CatalogDto catalog();

    public DomainDto saveDomain(String id, DomainDto request);

    public ProgramDto saveProgram(String id, ProgramDto request);

    public CourseDto saveCourse(String id, CourseRequest r);

    public NextCodeDto getNextCode(String type, String module);

    public List<CourseDto> adminCourses();

    public List<CourseDto> adminCoursesByProgram(String programId);

    public PagedResult<CourseDto> adminCoursesByProgramPaged(String programId, String search, int page, int size, String unallocatedForUnitId);
    default PagedResult<CourseDto> adminCoursesByProgramPaged(String programId, String search, int page, int size) {
        return adminCoursesByProgramPaged(programId, search, page, size, null);
    }

    public PagedResult<ProgramListItem> adminProgramsPaged(String domainId, String search, int page, int size, String unallocatedForUnitId);
    default PagedResult<ProgramListItem> adminProgramsPaged(String domainId, String search, int page, int size) {
        return adminProgramsPaged(domainId, search, page, size, null);
    }

    public void allocateCoursesToUnit(String unitId, String grantType, String programId, boolean allInProgram, List<String> courseIds);

    public UnitAllocationResultDto assignUnitAllocation(
            String unitId,
            String categoryType,
            String grantType,
            String domainId,
            String programId,
            boolean allInProgram,
            List<String> courseIds);

    public UnitAllocationResultDto toggleUnitAllocationGrantType(
            String unitId,
            String categoryType,
            String targetId,
            String grantType);

    public UnitAllocationRemovalDto removeUnitAllocation(
            String unitId,
            String categoryType,
            String targetId);

    public List<CourseDto> adminCoursesByUnit(String unitId);

    public List<ChapterDto> adminChapters(String courseId);

    public ChapterDto adminChapter(String id);

    public ChapterDto saveChapter(String courseId, String id, ChapterRequest r);

    public void deleteDomain(String id);

    public void deleteProgram(String id);

    public void deleteChapter(String id);

    public void deleteLesson(String id);

    public void deleteCourse(String id);

    public void reorderChapters(String id, List<String> ids);

    public void reorderLessons(String id, List<String> ids);

    public CourseDto status(String id, String status);

    public OutlineDto outline(String id, String username);

    public void requireLesson(String id, String username);

    public void requireCourse(String id, String username);

    public int countActiveStaffInUnitsNative(java.util.Collection<String> unitIds);

    public PagedResult<DomainListItem> adminDomainsPaged(String search, int page, int size, String unallocatedForUnitId);
    default PagedResult<DomainListItem> adminDomainsPaged(String search, int page, int size) {
        return adminDomainsPaged(search, page, size, null);
    }

    public BatchAllocationResultDto batchAssignUnitAllocation(String unitId, List<BatchAllocationItem> items);

    public BatchSyncResultDto batchSyncUnitAllocations(String unitId, List<BatchChangeItem> changes);

    public UnitAllocationDirectoryDto getUnitAllocationDirectoryPaged(String search, String type, String status,
            int page, int size);

    public List<Map<String, Object>> getUnitAllocatedItems(String unitId);
}
