package com.bidv.coretraining.training.service.impl;

import com.bidv.coretraining.mapper.CourseMapper;
import com.bidv.coretraining.mapper.LessonOutlineMapper;
import com.bidv.coretraining.policy.CourseEditPolicy;
import com.bidv.coretraining.policy.LearningPolicy;
import com.bidv.coretraining.policy.LessonContentPolicy;
import com.bidv.coretraining.security.AccessPolicy;


import com.bidv.coretraining.training.dto.TrainingStructureDto.*;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.dto.CourseDto;
import com.bidv.coretraining.course.entity.Chapter;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.ChapterRepository;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.course.service.CourseImageService;
import com.bidv.coretraining.exception.*;
import com.bidv.coretraining.learner.service.LearningAccessService;
import com.bidv.coretraining.learner.service.LearningOutlineService;
import com.bidv.coretraining.lesson.dto.LessonDto;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.repository.LessonProgressRepository;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.training.dto.PagedResult;
import com.bidv.coretraining.training.entity.TrainingDomain;
import com.bidv.coretraining.training.entity.TrainingProgram;
import com.bidv.coretraining.training.repository.TrainingDomainRepository;
import com.bidv.coretraining.training.repository.TrainingProgramRepository;
import com.bidv.coretraining.training.repository.UnitAllocationNativeRepository;
import com.bidv.coretraining.training.service.TrainingStructureService;
import com.bidv.coretraining.user.entity.OrganizationUnit;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class TrainingStructureServiceImpl implements TrainingStructureService {

    private final LearningOutlineService learningOutline;
    private final LearningAccessService learningAccess;

    private final CourseImageService courseImages;

    private final AuthorizationService authorization;
    private final UnitAllocationNativeRepository catalogNative;
    private final TrainingDomainRepository domains;
    private final TrainingProgramRepository programs;
    private final CourseRepository courses;
    private final ChapterRepository chapters;
    private final LessonRepository lessons;
    private final UserRepository users;
    private final CourseMapper mapper;
    private final LessonProgressRepository progress;

    public TrainingStructureServiceImpl(TrainingDomainRepository domains, TrainingProgramRepository programs,
            CourseRepository courses, ChapterRepository chapters, LessonRepository lessons, UserRepository users,
            CourseMapper mapper,
            LearningOutlineService learningOutline, LearningAccessService learningAccess, CourseImageService courseImages,
            AuthorizationService authorization,
            LessonProgressRepository progress, UnitAllocationNativeRepository catalogNative) {
        this.domains = domains;
        this.programs = programs;
        this.courses = courses;
        this.chapters = chapters;
        this.lessons = lessons;
        this.users = users;
        this.mapper = mapper;
        this.learningOutline = learningOutline;
        this.learningAccess = learningAccess;
        this.courseImages = courseImages;
        this.authorization = authorization;
        this.progress = progress;
        this.catalogNative = catalogNative;
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank())
            throw new BadRequestException(label + " không được để trống");
        return value.trim();
    }

    private void draft(Course course) {
        CourseEditPolicy.requireDraft(course);
    }

    private void version(Long actual, Long expected) {
        if (expected != null && !Objects.equals(actual, expected))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Dữ liệu đã thay đổi. Hãy tải lại trang");
    }

    public Course course(String id) {
        return courses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
    }

    public Chapter chapter(String id) {
        return chapters.findById(id).orElseThrow(() -> new ResourceNotFoundException("Chapter", "id", id));
    }

    public Lesson lesson(String id) {
        return lessons.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
    }

    public User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    public ProgramDto programDto(TrainingProgram p) {
        var u = authorization.current();
        // Units manage a program; learners get courses only through course allocation.
        Set<String> allUnits = new HashSet<>(p.getUnits().stream().map(OrganizationUnit::getId).toList());
        Set<String> mandatoryUnits = Set.of();
        Set<String> optionalUnits = allUnits;

        return new ProgramDto(
                p.getId(),
                p.getDomain() == null ? null : p.getDomain().getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getStatus(),
                allUnits,
                java.util.stream.Stream.of("read", "create", "update", "delete")
                        .filter(a -> AccessPolicy.allowsProgram(u, "catalog:" + a, p))
                        .collect(java.util.stream.Collectors.toSet()),
                p.getBusinessModule(),
                p.getLevel(),
                mandatoryUnits,
                optionalUnits,
                false,
                p.getSeqNumber());
    }

    private boolean canUpdateDomain(TrainingDomain d) {
        var u = authorization.current();
        if (AccessPolicy.system(u)) {
            return true;
        }
        return AccessPolicy.has(u, "catalog:update")
                && programs.findByDomainId(d.getId()).stream()
                        .filter(p -> !"DELETED".equals(p.getStatus()))
                        .allMatch(p -> AccessPolicy.allowsProgram(u, "catalog:update", p));
    }

    private DomainDto domainDto(TrainingDomain d) {
        Set<String> actions = new HashSet<>();
        actions.add("read");
        if (AccessPolicy.has(authorization.current(), "catalog:create"))
            actions.add("create");
        if (canUpdateDomain(d))
            actions.add("update");

        Set<String> allUnits = new HashSet<>(d.getUnits().stream().map(OrganizationUnit::getId).toList());
        Set<String> mandatoryUnits = Set.of();
        Set<String> optionalUnits = allUnits;

        return new DomainDto(d.getId(), d.getCode(), d.getName(), d.getDescription(), d.getStatus(), actions,
                d.getBusinessModule(), d.getSeqNumber(), allUnits, mandatoryUnits, optionalUnits);
    }

    public CatalogDto catalog() {
        return new CatalogDto(domains.findAll().stream()
                        .filter(d -> !"DELETED".equals(d.getStatus()))
                        .map(this::domainDto).toList(),
                programs
                        .findAll().stream()
                        .filter(p -> !"DELETED".equals(p.getStatus()))
                        .filter(p -> AccessPolicy
                                .allowsProgram(authorization.current(), "catalog:read", p))
                        .map(p -> programDto(p)).toList());
    }

    public DomainDto saveDomain(String id, DomainDto request) {
        if (id != null && request != null && "DELETED".equalsIgnoreCase(request.status())) {
            deleteDomain(id);
            TrainingDomain deleted = domains.findById(id).orElseThrow(() -> new ResourceNotFoundException("Domain", "id", id));
            return domainDto(deleted);
        }
        authorization.permission("CATALOG_MANAGE");
        TrainingDomain d = id == null ? TrainingDomain.builder().build()
                : domains.findById(id).orElseThrow(() -> new ResourceNotFoundException("Domain", "id", id));
        if (id != null)
            AccessPolicy.require(canUpdateDomain(d));

        Set<String> mandatoryIds = request.mandatoryUnitIds() != null ? new HashSet<>(request.mandatoryUnitIds())
                : new HashSet<>();
        Set<String> optionalIds = request.optionalUnitIds() != null ? new HashSet<>(request.optionalUnitIds())
                : new HashSet<>();

        if (request.unitIds() != null) {
            for (String uId : request.unitIds()) {
                if (!mandatoryIds.contains(uId) && !optionalIds.contains(uId)) {
                    optionalIds.add(uId);
                }
            }
        }

        Set<String> unitIds = new HashSet<>(mandatoryIds);
        unitIds.addAll(optionalIds);
        d.setUnits(managedUnits(unitIds));

        String mod = clean(request.businessModule());
        d.setBusinessModule(mod);

        if (id == null && mod != null && !mod.isBlank()) {
            Integer nextSeq = domains.findMaxSeqNumberByBusinessModule(mod) + 1;
            String candidateCode = mod + "LV" + nextSeq;
            while (domains.existsByCode(candidateCode)) {
                nextSeq++;
                candidateCode = mod + "LV" + nextSeq;
            }
            if (request.code() != null && !request.code().isBlank() && !domains.existsByCode(request.code().trim().toUpperCase())) {
                d.setCode(request.code().trim().toUpperCase());
                d.setSeqNumber(extractTrailingNumber(d.getCode(), nextSeq));
            } else {
                d.setCode(candidateCode);
                d.setSeqNumber(nextSeq);
            }
        } else {
            String dCode = (request.code() != null && !request.code().isBlank()) ? request.code().trim() : (d.getCode() != null ? d.getCode() : java.util.UUID.randomUUID().toString());
            d.setCode(dCode);
            if (d.getSeqNumber() == null) {
                d.setSeqNumber(extractTrailingNumber(d.getCode(), 1));
            }
        }

        d.setName(required(request.name(), "Tên lĩnh vực"));
        d.setDescription(request.description());
        d.setStatus("DELETED".equals(request.status()) ? "DELETED" : ("ARCHIVED".equals(request.status()) ? "ARCHIVED" : "ACTIVE"));
        domains.save(d);
        return domainDto(d);
    }

    public ProgramDto saveProgram(String id, ProgramDto request) {
        if (id != null && request != null && "DELETED".equalsIgnoreCase(request.status())) {
            deleteProgram(id);
            TrainingProgram deleted = programs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));
            return programDto(deleted);
        }
        authorization.permission("CATALOG_MANAGE");
        TrainingProgram p = id == null ? TrainingProgram.builder().build()
                : programs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));
        if (id != null)
            authorization.program(p, "catalog", "update");

        Set<String> mandatoryIds = request.mandatoryUnitIds() != null ? new HashSet<>(request.mandatoryUnitIds())
                : new HashSet<>();
        Set<String> optionalIds = request.optionalUnitIds() != null ? new HashSet<>(request.optionalUnitIds())
                : new HashSet<>();

        if (request.unitIds() != null) {
            for (String uId : request.unitIds()) {
                if (!mandatoryIds.contains(uId) && !optionalIds.contains(uId)) {
                    optionalIds.add(uId);
                }
            }
        }

        // A unit-scoped actor keeps its own unit so it can still manage the program; nobody else gets an implicit grant.
        OrganizationUnit actorUnit = authorization.current().getOrganizationUnit();
        if (mandatoryIds.isEmpty() && optionalIds.isEmpty() && actorUnit != null) {
            optionalIds.add(actorUnit.getId());
        }

        if (id != null) {
            for (OrganizationUnit old : p.getUnits()) {
                authorization.scope(old);
            }
        }

        Set<String> unitIds = new HashSet<>(mandatoryIds);
        unitIds.addAll(optionalIds);
        p.setUnits(managedUnits(unitIds));
        p.setDomain(domains.findById(required(request.domainId(), "Lĩnh vực"))
                .orElseThrow(() -> new BadRequestException("Lĩnh vực không tồn tại")));

        p.setName(required(request.name(), "Tên chương trình"));
        p.setDescription(request.description());

        String mod = clean(request.businessModule());
        p.setBusinessModule(mod);
        p.setLevel(clean(request.level()));
        p.setStatus("DELETED".equals(request.status()) ? "DELETED" : ("ARCHIVED".equals(request.status()) ? "ARCHIVED" : "ACTIVE"));

        if (id == null && mod != null && !mod.isBlank()) {
            Integer nextSeq = programs.findMaxSeqNumberByBusinessModule(mod) + 1;
            String candidateCode = mod + "CT" + nextSeq;
            while (programs.existsByCode(candidateCode)) {
                nextSeq++;
                candidateCode = mod + "CT" + nextSeq;
            }
            if (request.code() != null && !request.code().isBlank() && !programs.existsByCode(request.code().trim().toUpperCase())) {
                p.setCode(request.code().trim().toUpperCase());
                p.setSeqNumber(extractTrailingNumber(p.getCode(), nextSeq));
            } else {
                p.setCode(candidateCode);
                p.setSeqNumber(nextSeq);
            }
        } else {
            String pCode = (request.code() != null && !request.code().isBlank()) ? request.code().trim() : (p.getCode() != null ? p.getCode() : java.util.UUID.randomUUID().toString());
            p.setCode(pCode);
            if (p.getSeqNumber() == null) {
                p.setSeqNumber(extractTrailingNumber(p.getCode(), 1));
            }
        }
        programs.save(p);

        return programDto(p);
    }

    private Set<OrganizationUnit> managedUnits(Set<String> unitIds) {
        Set<OrganizationUnit> result = new HashSet<>();
        for (String unitId : unitIds) {
            OrganizationUnit n = authorization.unit(unitId);
            authorization.scope(n);
            if (!AccessPolicy.activeUnit(n))
                throw new BadRequestException("Đơn vị " + n.getName() + " đã ngừng hoạt động");
            result.add(n);
        }
        return result;
    }

    private void enrollStaffForCourse(Course course, Set<String> mandatoryUnitIds) {
        if (course != null && course.getId() != null) {
            catalogNative.enrollStaffForCourses(List.of(course.getId()), mandatoryUnitIds);
        }
    }

    private void unenrollUnstartedStaffForCourse(Course course, Set<String> unitIds) {
        if (course != null && course.getId() != null) {
            catalogNative.unenrollUnstartedStaff(List.of(course.getId()), unitIds);
        }
    }

    private void ensureCourseLearnableWhenAllocated(Course c) {
        if ("DRAFT".equalsIgnoreCase(c.getStatus())) {
            c.setStatus("PUBLISHED");
        }
        if (c.getChapters() != null) {
            for (Chapter ch : c.getChapters()) {
                if ("DRAFT".equalsIgnoreCase(ch.getStatus())) {
                    ch.setStatus("PUBLISHED");
                }
                if (ch.getLessons() != null) {
                    for (Lesson l : ch.getLessons()) {
                        if ("DRAFT".equalsIgnoreCase(l.getStatus()) || l.getStatus() == null) {
                            LessonContentPolicy.requirePublishable(l);
                            l.setStatus("PUBLISHED");
                        }
                    }
                }
            }
        }
    }

    public CourseDto saveCourse(String id, CourseRequest r) {
        Course c = id == null
                ? Course.builder().id(UUID.randomUUID().toString())
                        .ownerUnit(authorization.current().getOrganizationUnit()).build()
                : course(id);
        if (id == null) {
            authorization.permission("COURSE_MANAGE");
        } else
            authorization.edit(c);
        version(c.getVersion(), r.version());
        TrainingProgram p = programs.findById(required(r.programId(), "Chương trình"))
                .orElseThrow(() -> new BadRequestException("Chương trình không tồn tại"));
        if (!"ACTIVE".equals(p.getStatus()) || !"ACTIVE".equals(p.getDomain().getStatus()))
            throw new BadRequestException("Danh mục đã lưu trữ");
        authorization.program(p, "courses", id == null ? "create" : "update");
        c.setProgram(p);
        c.setName(required(r.name(), "Tên khóa"));
        c.setDescription(r.description());
        c.setIconName(r.iconName() == null ? "BookOpen" : r.iconName());
        courseImages.assertAttach(r.thumbnail(), c.getThumbnail());
        c.setThumbnail(r.thumbnail() == null || r.thumbnail().isBlank() ? null : r.thumbnail());
        String mod = clean(r.businessModule() != null ? r.businessModule() : p.getBusinessModule());
        c.setBusinessModule(mod);
        c.setLevel(clean(r.level() != null ? r.level() : p.getLevel()));
        c.setKeywords(clean(r.keywords()));
        c.setCompletionDeadline(r.completionDeadline());

        if (id == null && mod != null && !mod.isBlank()) {
            Integer nextSeq = courses.findMaxSeqNumberByBusinessModule(mod) + 1;
            String candidateCode = mod + "KH" + nextSeq;
            while (courses.existsByCode(candidateCode)) {
                nextSeq++;
                candidateCode = mod + "KH" + nextSeq;
            }
            if (r.code() != null && !r.code().isBlank() && !courses.existsByCode(r.code().trim().toUpperCase())) {
                c.setCode(r.code().trim().toUpperCase());
                c.setSeqNumber(extractTrailingNumber(c.getCode(), nextSeq));
            } else {
                c.setCode(candidateCode);
                c.setSeqNumber(nextSeq);
            }
        } else {
            String cCode = (r.code() != null && !r.code().isBlank()) ? r.code().trim() : (c.getCode() != null ? c.getCode() : java.util.UUID.randomUUID().toString());
            c.setCode(cCode);
            if (c.getSeqNumber() == null) {
                c.setSeqNumber(extractTrailingNumber(c.getCode(), 1));
            }
        }

        if (r.mandatoryUnitIds() != null || r.optionalUnitIds() != null || r.unitIds() != null) {
            Set<String> mandatoryIds = r.mandatoryUnitIds() != null ? new HashSet<>(r.mandatoryUnitIds()) : new HashSet<>();
            Set<String> optionalIds = r.optionalUnitIds() != null ? new HashSet<>(r.optionalUnitIds()) : new HashSet<>();
            if (r.unitIds() != null) {
                for (String uId : r.unitIds()) {
                    if (!mandatoryIds.contains(uId) && !optionalIds.contains(uId)) {
                        optionalIds.add(uId);
                    }
                }
            }

            Map<String, String> grants = new HashMap<>();
            for (String mId : mandatoryIds) {
                grants.put(mId, "MANDATORY");
            }
            for (String oId : optionalIds) {
                grants.put(oId, "OPTIONAL");
            }
            c.getUnitGrants().clear();
            c.getUnitGrants().putAll(grants);
        }

        Course saved = courses.saveAndFlush(c);

        // Auto-enroll staff from mandatory units
        Set<String> mandatoryUnitIds = saved.getUnitGrants().entrySet().stream()
                .filter(e -> "MANDATORY".equalsIgnoreCase(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        if (!mandatoryUnitIds.isEmpty()) {
            enrollStaffForCourse(saved, mandatoryUnitIds);
        }

        CourseDto resultDto = mapper.toCourseDto(saved);
        Set<String> optionalUnitIds = saved.getUnitGrants().entrySet().stream()
                .filter(e -> !"MANDATORY".equalsIgnoreCase(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        resultDto.setMandatoryUnitIds(mandatoryUnitIds);
        resultDto.setOptionalUnitIds(optionalUnitIds);
        Set<String> allUnits = new HashSet<>(mandatoryUnitIds);
        allUnits.addAll(optionalUnitIds);
        resultDto.setUnitIds(allUnits);
        return resultDto;
    }

    private Integer extractTrailingNumber(String code, Integer fallback) {
        if (code == null) return fallback;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)$").matcher(code.trim());
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    public NextCodeDto getNextCode(String type, String module) {
        String mod = clean(module);
        if (mod == null || mod.isBlank()) {
            return new NextCodeDto("", 0, null);
        }
        mod = mod.toUpperCase();
        String prefix;
        int nextSeq = 1;
        if ("domain".equalsIgnoreCase(type)) {
            prefix = mod + "LV";
            nextSeq = domains.findMaxSeqNumberByBusinessModule(mod) + 1;
            while (domains.existsByCode(prefix + nextSeq)) {
                nextSeq++;
            }
        } else if ("course".equalsIgnoreCase(type)) {
            prefix = mod + "KH";
            nextSeq = courses.findMaxSeqNumberByBusinessModule(mod) + 1;
            while (courses.existsByCode(prefix + nextSeq)) {
                nextSeq++;
            }
        } else if ("chapter".equalsIgnoreCase(type)) {
            prefix = mod + "CH";
            nextSeq = 1;
            while (chapters.existsById(prefix + nextSeq)) {
                nextSeq++;
            }
        } else if ("lesson".equalsIgnoreCase(type)) {
            prefix = mod + "BH";
            nextSeq = 1;
            while (lessons.existsById(prefix + nextSeq)) {
                nextSeq++;
            }
        } else { // default program
            prefix = mod + "CT";
            nextSeq = programs.findMaxSeqNumberByBusinessModule(mod) + 1;
            while (programs.existsByCode(prefix + nextSeq)) {
                nextSeq++;
            }
        }
        return new NextCodeDto(prefix + nextSeq, nextSeq, prefix);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public List<CourseDto> adminCourses() {
        return authorization.editableCourses().stream().map(this::mapToAdminCourseDto).toList();
    }

    public List<CourseDto> adminCoursesByProgram(String programId) {
        return courses.findByProgramIdAndStatusNot(programId, "DELETED").stream()
                .map(this::mapToAdminCourseDto)
                .toList();
    }

    @Override
    public PagedResult<DomainListItem> adminDomainsPaged(String search, int page, int size, String unallocatedForUnitId) {
        if (unallocatedForUnitId != null && !unallocatedForUnitId.isBlank()) {
            return catalogNative.adminDomainsPagedUnallocated(search, page, size, unallocatedForUnitId.trim());
        }
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<TrainingDomain> result = domains.findByFilter(query, pageable);
        return PagedResult.of(result, d -> DomainListItem.of(domainDto(d),
                programs.countByDomainIdAndStatusNot(d.getId(), "DELETED")));
    }

    @Override
    public PagedResult<CourseDto> adminCoursesByProgramPaged(String programId, String search, int page, int size, String unallocatedForUnitId) {
        if (unallocatedForUnitId != null && !unallocatedForUnitId.isBlank()) {
            return catalogNative.adminCoursesByProgramPagedUnallocated(programId, search, page, size, unallocatedForUnitId.trim());
        }
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return PagedResult.of(courses.findByProgramIdWithFilter(programId, query, pageable), this::mapToAdminCourseDto);
    }

    @Override
    public PagedResult<ProgramListItem> adminProgramsPaged(String domainId, String search, int page, int size, String unallocatedForUnitId) {
        if (unallocatedForUnitId != null && !unallocatedForUnitId.isBlank()) {
            return catalogNative.adminProgramsPagedUnallocated(domainId, search, page, size, unallocatedForUnitId.trim());
        }
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        String dId = (domainId != null && !domainId.isBlank()) ? domainId.trim() : null;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return PagedResult.of(programs.findByDomainIdWithFilter(dId, query, pageable),
                p -> ProgramListItem.of(programDto(p), courses.countByProgramIdAndStatusNot(p.getId(), "DELETED")));
    }

    @Override
    public List<Map<String, Object>> getUnitAllocatedItems(String unitId) {
        return catalogNative.getUnitAllocatedItems(unitId);
    }

    public void allocateCoursesToUnit(String unitId, String grantType, String programId, boolean allInProgram, List<String> courseIds) {
        assignUnitAllocation(unitId, "course", grantType, null, programId, allInProgram, courseIds);
    }

    private List<Course> domainCourses(String domainId) {
        List<Course> result = new ArrayList<>();
        for (TrainingProgram p : programs.findByDomainId(domainId)) {
            if (!"DELETED".equals(p.getStatus())) {
                result.addAll(courses.findByProgramIdAndStatusNot(p.getId(), "DELETED"));
            }
        }
        return result;
    }

    public UnitAllocationResultDto assignUnitAllocation(
            String unitId,
            String categoryType,
            String grantType,
            String domainId,
            String programId,
            boolean allInProgram,
            List<String> courseIds) {
        authorization.permission("CATALOG_MANAGE");
        OrganizationUnit unit = authorization.unit(required(unitId, "Đơn vị áp dụng"));
        authorization.scope(unit);
        if (!AccessPolicy.activeUnit(unit)) {
            throw new BadRequestException("Đơn vị " + unit.getName() + " đã ngừng hoạt động");
        }

        String normalizedGrant = "MANDATORY".equalsIgnoreCase(grantType) ? "MANDATORY" : "OPTIONAL";
        String type = categoryType == null ? "program" : categoryType.trim().toLowerCase();
        List<Course> targetCourses = new ArrayList<>();

        // Choosing a domain or program is a shortcut for its current courses: only course grants are stored.
        if ("domain".equals(type)) {
            TrainingDomain d = domains.findById(required(domainId, "Lĩnh vực đào tạo"))
                    .orElseThrow(() -> new ResourceNotFoundException("Domain", "id", domainId));
            targetCourses.addAll(domainCourses(d.getId()));
        } else if ("program".equals(type)) {
            TrainingProgram p = programs.findById(required(programId, "Chương trình đào tạo"))
                    .orElseThrow(() -> new ResourceNotFoundException("Program", "id", programId));
            targetCourses.addAll(courses.findByProgramIdAndStatusNot(p.getId(), "DELETED"));
        } else if ("course".equals(type)) {
            if (allInProgram) {
                String pId = required(programId, "Chương trình đào tạo");
                TrainingProgram p = programs.findById(pId)
                        .orElseThrow(() -> new ResourceNotFoundException("Program", "id", pId));
                targetCourses.addAll(courses.findByProgramIdAndStatusNot(p.getId(), "DELETED"));
            } else {
                if (courseIds == null || courseIds.isEmpty()) {
                    throw new BadRequestException("Vui lòng chọn ít nhất 1 khóa học để phân bổ");
                }
                targetCourses.addAll(courses.findAllById(courseIds).stream()
                        .filter(c -> !"DELETED".equals(c.getStatus()))
                        .toList());
            }
        } else {
            throw new BadRequestException("Cấp độ phân bổ không hợp lệ: " + categoryType);
        }

        for (Course c : targetCourses) {
            if (c.getUnitGrants() == null) {
                c.setUnitGrants(new HashMap<>());
            }
            c.getUnitGrants().put(unit.getId(), normalizedGrant);
            ensureCourseLearnableWhenAllocated(c);
            courses.save(c);
        }

        List<String> targetCourseIds = targetCourses.stream().map(Course::getId).toList();
        int unitUsersCount = catalogNative.countActiveStaffInUnits(Set.of(unit.getId()));
        int enrolledUsersCount = 0;

        if ("MANDATORY".equals(normalizedGrant)) {
            // Bắt buộc: Tự động ghi danh tức thì toàn bộ nhân sự phòng ban (và phòng ban con) qua 1 câu Native SQL duy nhất
            if (!targetCourseIds.isEmpty()) {
                catalogNative.enrollStaffForCourses(targetCourseIds, Set.of(unit.getId()));
                enrolledUsersCount = unitUsersCount;
            }
        } else {
            // Tự chọn (OPTIONAL): Rút đăng ký những người chưa bắt đầu học qua 1 câu Native SQL duy nhất
            if (!targetCourseIds.isEmpty()) {
                catalogNative.unenrollUnstartedStaff(targetCourseIds, Set.of(unit.getId()));
            }
        }

        return new UnitAllocationResultDto(unit.getId(), unit.getName(), type, normalizedGrant, targetCourses.size(),
                unitUsersCount, enrolledUsersCount);
    }

    @org.springframework.transaction.annotation.Transactional
    public BatchAllocationResultDto batchAssignUnitAllocation(
            String unitId,
            List<BatchAllocationItem> items) {
        authorization.permission("CATALOG_MANAGE");
        OrganizationUnit unit = authorization.unit(required(unitId, "Đơn vị áp dụng"));
        authorization.scope(unit);
        if (!com.bidv.coretraining.security.AccessPolicy.activeUnit(unit)) {
            throw new BadRequestException("Đơn vị " + unit.getName() + " đã ngừng hoạt động");
        }

        if (items == null || items.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ít nhất 1 nội dung để phân bổ");
        }

        Set<String> mandatoryCourseIds = new HashSet<>();
        Set<String> optionalCourseIds = new HashSet<>();
        Map<String, Course> coursesToSave = new HashMap<>();

        int domainsCount = 0;
        int programsCount = 0;
        int coursesCount = 0;

        // Process items in specificity order: domain first, then program, then individual courses
        // so that specific child grant types override broader parent grant types.
        List<BatchAllocationItem> sortedItems = items.stream()
                .filter(i -> i != null && i.categoryType() != null && i.targetId() != null)
                .sorted(java.util.Comparator.comparingInt(i -> {
                    String t = i.categoryType().trim().toLowerCase();
                    if ("domain".equals(t)) return 1;
                    if ("program".equals(t)) return 2;
                    return 3;
                }))
                .toList();

        for (BatchAllocationItem item : sortedItems) {
            String type = item.categoryType().trim().toLowerCase();
            String grant = "MANDATORY".equalsIgnoreCase(item.grantType()) ? "MANDATORY" : "OPTIONAL";
            String targetId = item.targetId();


            Set<String> excluded = item.excludeIds() == null ? Set.of() : new HashSet<>(item.excludeIds());
            List<Course> selected;
            if ("domain".equals(type)) {
                domains.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("Domain", "id", targetId));
                selected = domainCourses(targetId);
                domainsCount++;
            } else if ("program".equals(type)) {
                programs.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("Program", "id", targetId));
                selected = courses.findByProgramIdAndStatusNot(targetId, "DELETED");
                programsCount++;
            } else if ("course".equals(type)) {
                Course c = courses.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Course", "id", targetId));
                selected = "DELETED".equals(c.getStatus()) ? List.of() : List.of(c);
                coursesCount += selected.size();
            } else {
                continue;
            }
            for (Course c : selected) {
                if (excluded.contains(c.getId())) continue;
                Course cToSave = coursesToSave.computeIfAbsent(c.getId(), cid -> c);
                cToSave.getUnitGrants().put(unit.getId(), grant);
                if ("MANDATORY".equals(grant)) {
                    mandatoryCourseIds.add(cToSave.getId());
                    optionalCourseIds.remove(cToSave.getId());
                } else {
                    optionalCourseIds.add(cToSave.getId());
                    mandatoryCourseIds.remove(cToSave.getId());
                }
            }
        }

        for (Course c : coursesToSave.values()) {
            ensureCourseLearnableWhenAllocated(c);
        }
        courses.saveAll(coursesToSave.values());

        int unitUsersCount = catalogNative.countActiveStaffInUnits(Set.of(unit.getId()));
        int enrolledUsersCount = 0;

        if (!mandatoryCourseIds.isEmpty()) {
            catalogNative.enrollStaffForCourses(new ArrayList<>(mandatoryCourseIds), Set.of(unit.getId()));
            enrolledUsersCount = unitUsersCount;
        }
        if (!optionalCourseIds.isEmpty()) {
            catalogNative.unenrollUnstartedStaff(new ArrayList<>(optionalCourseIds), Set.of(unit.getId()));
        }

        return new BatchAllocationResultDto(unit.getId(), unit.getName(), domainsCount, programsCount, coursesCount,
                coursesToSave.size(), unitUsersCount, enrolledUsersCount, "SUCCESS");
    }

    @org.springframework.transaction.annotation.Transactional
    public BatchSyncResultDto batchSyncUnitAllocations(
            String unitId,
            List<BatchChangeItem> changes) {
        authorization.permission("CATALOG_MANAGE");
        OrganizationUnit unit = authorization.unit(required(unitId, "Đơn vị áp dụng"));
        authorization.scope(unit);
        if (!com.bidv.coretraining.security.AccessPolicy.activeUnit(unit)) {
            throw new BadRequestException("Đơn vị " + unit.getName() + " đã ngừng hoạt động");
        }

        if (changes == null || changes.isEmpty()) {
            return BatchSyncResultDto.noChanges("Không có thay đổi nào cần lưu");
        }

        int updatedCount = 0;
        int removedCount = 0;

        for (BatchChangeItem item : changes) {
            if (item == null || item.categoryType() == null || item.targetId() == null) continue;
            String action = item.action() == null ? "UPDATE_GRANT" : item.action().trim().toUpperCase();
            String type = item.categoryType().trim().toLowerCase();
            String targetId = item.targetId();


            if ("REMOVE".equals(action)) {
                removeUnitAllocation(unitId, type, targetId);
                removedCount++;
            } else {
                String grant = "MANDATORY".equalsIgnoreCase(item.grantType()) ? "MANDATORY" : "OPTIONAL";
                toggleUnitAllocationGrantType(unitId, type, targetId, grant);
                updatedCount++;
            }
        }

        return new BatchSyncResultDto(unit.getId(), unit.getName(), updatedCount, removedCount,
                updatedCount + removedCount, "SUCCESS", null);
    }

    public UnitAllocationResultDto toggleUnitAllocationGrantType(
            String unitId,
            String categoryType,
            String targetId,
            String grantType) {
        String type = categoryType == null ? "course" : categoryType.trim().toLowerCase();
        if ("domain".equals(type)) {
            return assignUnitAllocation(unitId, "domain", grantType, targetId, null, false, List.of());
        } else if ("program".equals(type)) {
            return assignUnitAllocation(unitId, "program", grantType, null, targetId, true, List.of());
        } else {
            return assignUnitAllocation(unitId, "course", grantType, null, null, false, List.of(targetId));
        }
    }

    public UnitAllocationRemovalDto removeUnitAllocation(
            String unitId,
            String categoryType,
            String targetId) {
        authorization.permission("CATALOG_MANAGE");
        OrganizationUnit unit = authorization.unit(required(unitId, "Đơn vị"));
        authorization.scope(unit);

        String type = categoryType == null ? "course" : categoryType.trim().toLowerCase();
        List<Course> affectedCourses = new ArrayList<>();

        if ("domain".equals(type)) {
            TrainingDomain d = domains.findById(required(targetId, "Lĩnh vực"))
                    .orElseThrow(() -> new ResourceNotFoundException("Domain", "id", targetId));
            affectedCourses.addAll(domainCourses(d.getId()));
        } else if ("program".equals(type)) {
            TrainingProgram p = programs.findById(required(targetId, "Chương trình"))
                    .orElseThrow(() -> new ResourceNotFoundException("Program", "id", targetId));
            affectedCourses.addAll(courses.findByProgramIdAndStatusNot(p.getId(), "DELETED"));
        } else {
            Course c = course(required(targetId, "Khóa học"));
            affectedCourses.add(c);
        }

        for (Course c : affectedCourses) {
            if (c.getUnitGrants() != null) {
                c.getUnitGrants().remove(unitId);
                courses.save(c);
            }
        }
        if (!affectedCourses.isEmpty()) {
            List<String> cIds = affectedCourses.stream().map(Course::getId).toList();
            catalogNative.unenrollUnstartedStaff(cIds, Set.of(unitId));
        }

        return new UnitAllocationRemovalDto(unitId, affectedCourses.size());
    }

    public List<CourseDto> adminCoursesByUnit(String unitId) {
        return courses.findByUnitGrant(unitId).stream()
                .filter(c -> !"DELETED".equals(c.getStatus()))
                .map(this::mapToAdminCourseDto)
                .toList();
    }

    private CourseDto mapToAdminCourseDto(Course c) {
        CourseDto dto = mapper.toCourseDto(c);
        dto.setCanEdit(AccessPolicy.canEdit(authorization.current(), c));
        dto.setAllowedActions(
                AccessPolicy.courseActions(authorization.current(), c));
        dto.setAdminActions(java.util.stream.Stream.of("courses", "exams", "documents")
                .collect(java.util.stream.Collectors.toMap(p -> p,
                        p -> java.util.stream.Stream.of("read", "create", "update", "delete")
                                 .filter(a -> AccessPolicy
                                        .allowsCourse(authorization.current(), p + ":" + a, c))
                                .collect(java.util.stream.Collectors.toSet()))));
        if (!AccessPolicy.canReadCourse(authorization.current(), c))
            dto.getLessons().forEach(LessonOutlineMapper::outlineOnly);

        Set<String> mandatory = new HashSet<>();
        Set<String> optional = new HashSet<>();
        if (c.getUnitGrants() != null && !c.getUnitGrants().isEmpty()) {
            for (var entry : c.getUnitGrants().entrySet()) {
                if ("MANDATORY".equalsIgnoreCase(entry.getValue())) {
                    mandatory.add(entry.getKey());
                } else {
                    optional.add(entry.getKey());
                }
            }
        }
        dto.setMandatoryUnitIds(mandatory);
        dto.setOptionalUnitIds(optional);
        Set<String> allUnits = new HashSet<>(mandatory);
        allUnits.addAll(optional);
        dto.setUnitIds(allUnits);

        return dto;
    }

    public List<ChapterDto> adminChapters(String courseId) {
        authorization.edit(courseId);
        return chapters.findByCourseIdOrderByChapterOrderAscIdAsc(courseId).stream()
                .filter(c -> !"DELETED".equals(c.getStatus()))
                .map(c -> chapterDto(c, null))
                .toList();
    }

    public ChapterDto adminChapter(String id) {
        Chapter c = chapter(id);
        authorization.edit(c.getCourse());
        return chapterDto(c, null);
    }

    private ChapterDto chapterDto(Chapter c, User user) {
        boolean admin = user == null || AccessPolicy.canReadCourse(user, c.getCourse());
        List<Lesson> list = (admin ? c.getLessons() : LearningPolicy.publishedLessons(c))
                .stream().filter(l -> !"DELETED".equals(l.getStatus())).toList();
        List<LessonDto> lessonDtos = mapper.toLessonDtoList(list);
        if (!admin)
            lessonDtos.forEach(LessonOutlineMapper::outlineOnly);
        return new ChapterDto(c.getId(), c.getCourse().getId(), c.getCode(), c.getTitle(), c.getDescription(),
                c.getChapterOrder(), c.getStatus(), c.getVersion(), lessonDtos,
                !admin && !LearningPolicy.chapterUnlocked(user, c),
                user != null && LearningPolicy.chapterCompleted(user, c));
    }

    public ChapterDto saveChapter(String courseId, String id, ChapterRequest r) {
        Course course = course(courseId);
        authorization.edit(course);
        draft(course);
        Chapter c = id == null
                ? Chapter.builder().course(course)
                        .chapterOrder(
                                course.getChapters().stream().mapToInt(Chapter::getChapterOrder).max().orElse(-1) + 1)
                        .build()
                : chapter(id);
        if (!c.getCourse().getId().equals(courseId))
            throw new BadRequestException("Chương không thuộc khóa");
        version(c.getVersion(), r.version());
        String chCode = (r.code() != null && !r.code().isBlank()) ? r.code().trim() : (c.getCode() != null ? c.getCode() : java.util.UUID.randomUUID().toString());
        c.setCode(chCode);
        c.setTitle(required(r.title(), "Tên chương"));
        c.setDescription(r.description());
        return chapterDto(chapters.saveAndFlush(c), null);
    }

    private void noHistory(List<Lesson> list) {
        Set<String> ids = new HashSet<>(list.stream().map(Lesson::getId).toList());
        if (!ids.isEmpty() && progress.existsByLessonIdIn(ids))
            throw new BadRequestException("Nội dung đã có tiến độ học. Hãy lưu trữ khóa thay vì xóa");
        if (!ids.isEmpty() && (users.anyCompletedLessonIn(ids) || users.anyQuizScoreForLessonIn(ids)))
            throw new BadRequestException("Nội dung đã có lịch sử học. Hãy lưu trữ khóa thay vì xóa");
    }

    public void deleteDomain(String id) {
        authorization.permission("CATALOG_MANAGE");
        domains.findById(id).orElseThrow(() -> new ResourceNotFoundException("Domain", "id", id));
        String suffix = "_DEL_" + (System.currentTimeMillis() % 100000);
        int negBase = -Math.abs((int) (System.currentTimeMillis() % 900000) + 1000);
        catalogNative.unenrollUnstartedStaff(catalogNative.courseIdsInDomain(id));
        catalogNative.softDeleteDomainTree(id, suffix, negBase);
    }

    public void deleteProgram(String id) {
        authorization.permission("CATALOG_MANAGE");
        programs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));
        String suffix = "_DEL_" + (System.currentTimeMillis() % 100000);
        int negBase = -Math.abs((int) (System.currentTimeMillis() % 900000) + 1000);
        catalogNative.unenrollUnstartedStaff(catalogNative.courseIdsInProgram(id));
        catalogNative.softDeleteProgramTree(id, suffix, negBase);
    }

    public void deleteChapter(String id) {
        Chapter c = chapter(id);
        authorization.edit(c.getCourse());
        draft(c.getCourse());
        String suffix = "_DEL_" + (System.currentTimeMillis() % 100000);
        int negBase = -Math.abs((int) (System.currentTimeMillis() % 900000) + 1000);
        c.setStatus("DELETED");
        if (c.getCode() != null && !c.getCode().contains("_DEL_")) {
            c.setCode(c.getCode() + suffix);
        }
        c.setChapterOrder(negBase);
        int idx = 1;
        for (Lesson l : c.getLessons()) {
            l.setStatus("DELETED");
            l.setLessonOrder(negBase - (idx++));
        }
        chapters.saveAndFlush(c);
    }

    public void deleteLesson(String id) {
        Lesson l = lesson(id);
        authorization.edit(l.getCourse());
        draft(l.getCourse());
        int negOrder = -Math.abs((int) (System.currentTimeMillis() % 900000) + 1000);
        l.setStatus("DELETED");
        l.setLessonOrder(negOrder);
        lessons.saveAndFlush(l);
    }

    public void deleteCourse(String id) {
        Course c = course(id);
        authorization.edit(c);
        String suffix = "_DEL_" + (System.currentTimeMillis() % 100000);
        int negBase = -Math.abs((int) (System.currentTimeMillis() % 900000) + 1000);
        catalogNative.unenrollUnstartedStaff(Set.of(id));
        catalogNative.softDeleteCourseTree(id, suffix, negBase);
    }

    public void reorderChapters(String id, List<String> ids) {
        Course course = course(id);
        authorization.edit(course);
        draft(course);
        List<Chapter> current = chapters.findByCourseIdOrderByChapterOrderAscIdAsc(id);
        validateOrder(ids, current.stream().map(Chapter::getId).toList());
        for (int i = 0; i < current.size(); i++)
            current.get(i).setChapterOrder(-i - 1);
        chapters.flush();
        for (int i = 0; i < ids.size(); i++) {
            Chapter c = chapter(ids.get(i));
            c.setChapterOrder(i);
        }
        chapters.flush();
    }

    public void reorderLessons(String id, List<String> ids) {
        Chapter chapter = chapter(id);
        authorization.edit(chapter.getCourse());
        draft(chapter.getCourse());
        List<Lesson> current = lessons.findByChapterIdOrderByLessonOrderAscIdAsc(id);
        validateOrder(ids, current.stream().map(Lesson::getId).toList());
        for (int i = 0; i < current.size(); i++)
            current.get(i).setLessonOrder(-i - 1);
        lessons.flush();
        for (int i = 0; i < ids.size(); i++) {
            lesson(ids.get(i)).setLessonOrder(i);
        }
        lessons.flush();
    }

    private void validateOrder(List<String> ids, List<String> current) {
        if (ids == null || ids.size() != current.size() || new HashSet<>(ids).size() != ids.size()
                || !new HashSet<>(ids).equals(new HashSet<>(current)))
            throw new BadRequestException("Danh sách thứ tự phải đầy đủ, không trùng và cùng cấp");
    }

    public CourseDto status(String id, String status) {
        Course c = course(id);
        authorization.manage(c);
        if (!Set.of("DRAFT", "PUBLISHED", "ARCHIVED", "DELETED").contains(status))
            throw new BadRequestException("Trạng thái không hợp lệ");
        if ("PUBLISHED".equals(status)) {
            List<Chapter> activeChapters = c.getChapters().stream()
                    .filter(ch -> !"DELETED".equals(ch.getStatus())).toList();
            if (activeChapters.isEmpty())
                throw new BadRequestException("Khóa chưa có chương");
            for (Chapter ch : activeChapters) {
                List<Lesson> activeLessons = ch.getLessons().stream()
                        .filter(l -> !"DELETED".equals(l.getStatus())).toList();
                if (activeLessons.isEmpty())
                    throw new BadRequestException(chapterLabel(ch) + " chưa có bài học");
                for (Lesson l : activeLessons) {
                    LessonContentPolicy.requirePublishable(l);
                    l.setStatus("PUBLISHED");
                }
                ch.setStatus("PUBLISHED");
            }
        }
        c.setStatus(status);
        return mapper.toCourseDto(courses.save(c));
    }

    private String chapterLabel(Chapter ch) {
        String title = ch.getTitle() == null ? "" : ch.getTitle().trim();
        return title.toLowerCase(java.util.Locale.ROOT).startsWith("chương") ? title : "Chương " + title;
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    @Transactional(readOnly = true)
    public OutlineDto outline(String id, String username) {
        return learningOutline.outline(id, username);
    }

    @Transactional(readOnly = true)
    public void requireLesson(String id, String username) {
        learningAccess.requireLesson(username, id);
    }

    @Transactional(readOnly = true)
    public void requireCourse(String id, String username) {
        LearningPolicy.assertCourseAccess(user(username), course(id));
    }

    public int countActiveStaffInUnitsNative(Collection<String> unitIds) {
        return catalogNative.countActiveStaffInUnits(unitIds);
    }

    public UnitAllocationDirectoryDto getUnitAllocationDirectoryPaged(String search, String type, String status, int page, int size) {
        return catalogNative.unitAllocationDirectory(search, type, status, page, size);
    }

}
