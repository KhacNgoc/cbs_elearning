package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

@Service
@Transactional
public class TrainingStructureService {
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
            LearnerWorkspaceService.Resume resume) {
    }

    @org.springframework.beans.factory.annotation.Autowired
    private LearningOutlineService learningOutline;
    @org.springframework.beans.factory.annotation.Autowired
    private LearningAccessService learningAccess;

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

    @org.springframework.beans.factory.annotation.Autowired
    private CourseImageService courseImages;

    public record ChapterRequest(String code, String title, String description, Long version) {
    }

    @org.springframework.beans.factory.annotation.Autowired
    private AuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired
    private OrganizationUnitRepository organizationUnits;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final TrainingDomainRepository domains;
    private final TrainingProgramRepository programs;
    private final CourseRepository courses;
    private final ChapterRepository chapters;
    private final LessonRepository lessons;
    private final UserRepository users;
    private final CourseMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired
    private LessonProgressRepository progress;

    public TrainingStructureService(TrainingDomainRepository domains, TrainingProgramRepository programs,
            CourseRepository courses, ChapterRepository chapters, LessonRepository lessons, UserRepository users,
            CourseMapper mapper) {
        this.domains = domains;
        this.programs = programs;
        this.courses = courses;
        this.chapters = chapters;
        this.lessons = lessons;
        this.users = users;
        this.mapper = mapper;
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank())
            throw new BadRequestException(label + " không được để trống");
        return value.trim();
    }

    private void draft(Course course) {
        if ("DELETED".equals(course.getStatus()))
            throw new BadRequestException("Khóa học đã bị xóa");
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

        return new ProgramDto(
                p.getId(),
                p.getDomain() == null ? null : p.getDomain().getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getStatus(),
                allUnits,
                java.util.stream.Stream.of("read", "create", "update", "delete")
                        .filter(a -> com.bidv.coretraining.security.AccessPolicy.allowsProgram(u, "catalog:" + a, p))
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
        if (com.bidv.coretraining.security.AccessPolicy.system(u)) {
            return true;
        }
        return com.bidv.coretraining.security.AccessPolicy.has(u, "catalog:update")
                && programs.findByDomainId(d.getId()).stream()
                        .filter(p -> !"DELETED".equals(p.getStatus()))
                        .allMatch(p -> com.bidv.coretraining.security.AccessPolicy.allowsProgram(u, "catalog:update", p));
    }

    private DomainDto domainDto(TrainingDomain d) {
        Set<String> actions = new HashSet<>();
        actions.add("read");
        if (com.bidv.coretraining.security.AccessPolicy.has(authorization.current(), "catalog:create"))
            actions.add("create");
        if (canUpdateDomain(d))
            actions.add("update");

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

        return new DomainDto(d.getId(), d.getCode(), d.getName(), d.getDescription(), d.getStatus(), actions,
                d.getBusinessModule(), d.getSeqNumber(), allUnits, mandatoryUnits, optionalUnits);
    }

    public Map<String, Object> catalog() {
        return Map.of("domains", domains.findAll().stream()
                        .filter(d -> !"DELETED".equals(d.getStatus()))
                        .map(this::domainDto).toList(),
                "programs", programs
                        .findAll().stream()
                        .filter(p -> !"DELETED".equals(p.getStatus()))
                        .filter(p -> com.bidv.coretraining.security.AccessPolicy
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
            com.bidv.coretraining.security.AccessPolicy.require(canUpdateDomain(d));

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

        Set<OrganizationUnit> assigned = new HashSet<>();
        Map<String, String> grants = new HashMap<>();

        for (String mId : mandatoryIds) {
            OrganizationUnit n = authorization.unit(mId);
            authorization.scope(n);
            if (!com.bidv.coretraining.security.AccessPolicy.activeUnit(n))
                throw new BadRequestException("Đơn vị " + n.getName() + " đã ngừng hoạt động");
            assigned.add(n);
            grants.put(mId, "MANDATORY");
        }
        for (String optId : optionalIds) {
            OrganizationUnit n = authorization.unit(optId);
            authorization.scope(n);
            if (!com.bidv.coretraining.security.AccessPolicy.activeUnit(n))
                throw new BadRequestException("Đơn vị " + n.getName() + " đã ngừng hoạt động");
            assigned.add(n);
            grants.put(optId, "OPTIONAL");
        }

        d.setUnits(assigned);
        d.setUnitGrants(grants);

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

        // Fallback if no units specified but organization units exist
        if (mandatoryIds.isEmpty() && optionalIds.isEmpty() && organizationUnits != null && organizationUnits.count() > 0) {
            if (authorization.current().getOrganizationUnit() != null) {
                optionalIds.add(authorization.current().getOrganizationUnit().getId());
            } else {
                organizationUnits.findAll().stream().findFirst().ifPresent(u -> optionalIds.add(u.getId()));
            }
        }

        Set<OrganizationUnit> assigned = new HashSet<>();
        Map<String, String> grants = new HashMap<>();

        for (String mId : mandatoryIds) {
            OrganizationUnit n = authorization.unit(mId);
            authorization.scope(n);
            if (!com.bidv.coretraining.security.AccessPolicy.activeUnit(n))
                throw new BadRequestException("Đơn vị " + n.getName() + " đã ngừng hoạt động");
            assigned.add(n);
            grants.put(mId, "MANDATORY");
        }
        for (String optId : optionalIds) {
            OrganizationUnit n = authorization.unit(optId);
            authorization.scope(n);
            if (!com.bidv.coretraining.security.AccessPolicy.activeUnit(n))
                throw new BadRequestException("Đơn vị " + n.getName() + " đã ngừng hoạt động");
            assigned.add(n);
            grants.put(optId, "OPTIONAL");
        }

        if (id != null) {
            for (OrganizationUnit old : p.getUnits()) {
                authorization.scope(old);
            }
        }

        p.setUnits(assigned);
        p.setUnitGrants(grants);
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

        // Auto-enroll all staff from mandatory units into courses of this program
        if (!mandatoryIds.isEmpty() && !Boolean.FALSE.equals(request.autoEnrollStaff())) {
            enrollStaffForMandatoryUnits(p, mandatoryIds);
        }

        return programDto(p);
    }

    public int enrollStaffForCoursesNative(Collection<String> courseIds, Collection<String> unitIds) {
        if (courseIds == null || courseIds.isEmpty() || unitIds == null || unitIds.isEmpty()) {
            return 0;
        }
        String sql = """
            WITH RECURSIVE target_units AS (
                SELECT id FROM organization_units WHERE id IN (:unitIds)
                UNION ALL
                SELECT ou.id FROM organization_units ou INNER JOIN target_units tu ON ou.parent_id = tu.id
            )
            INSERT INTO user_registered_courses (user_id, course_id)
            SELECT u.id, c.id
            FROM users u
            CROSS JOIN courses c
            WHERE u.status = 'ACTIVE'
              AND u.unit_id IN (SELECT id FROM target_units)
              AND c.id IN (:courseIds)
            ON CONFLICT (user_id, course_id) DO NOTHING
            """;
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params =
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource();
        params.addValue("unitIds", unitIds);
        params.addValue("courseIds", courseIds);
        return namedParameterJdbcTemplate.update(sql, params);
    }

    public int unenrollUnstartedStaffForCoursesNative(Collection<String> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return 0;
        }
        String sql = """
            DELETE FROM user_registered_courses urc
            WHERE urc.course_id IN (:courseIds)
              AND NOT EXISTS (
                  SELECT 1
                  FROM user_completed_lessons ucl
                  JOIN lessons l ON ucl.lesson_id = l.id
                  WHERE ucl.user_id = urc.user_id AND l.course_id = urc.course_id
              )
            """;
        return namedParameterJdbcTemplate.update(sql, Map.of("courseIds", courseIds));
    }

    public int unenrollUnstartedStaffForCoursesNative(Collection<String> courseIds, Collection<String> unitIds) {
        if (courseIds == null || courseIds.isEmpty() || unitIds == null || unitIds.isEmpty()) {
            return 0;
        }
        String sql = """
            WITH RECURSIVE target_units AS (
                SELECT id FROM organization_units WHERE id IN (:unitIds)
                UNION ALL
                SELECT ou.id FROM organization_units ou INNER JOIN target_units tu ON ou.parent_id = tu.id
            )
            DELETE FROM user_registered_courses urc
            USING users u
            WHERE urc.user_id = u.id
              AND urc.course_id IN (:courseIds)
              AND u.unit_id IN (SELECT id FROM target_units)
              AND NOT EXISTS (
                  SELECT 1
                  FROM user_completed_lessons ucl
                  JOIN lessons l ON ucl.lesson_id = l.id
                  WHERE ucl.user_id = u.id AND l.course_id = urc.course_id
              )
            """;
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params =
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource();
        params.addValue("unitIds", unitIds);
        params.addValue("courseIds", courseIds);
        return namedParameterJdbcTemplate.update(sql, params);
    }

    public int countActiveStaffInUnitsNative(Collection<String> unitIds) {
        if (unitIds == null || unitIds.isEmpty()) return 0;
        String sql = """
            WITH RECURSIVE target_units AS (
                SELECT id FROM organization_units WHERE id IN (:unitIds)
                UNION ALL
                SELECT ou.id FROM organization_units ou INNER JOIN target_units tu ON ou.parent_id = tu.id
            )
            SELECT COUNT(*) FROM users u
            WHERE u.status = 'ACTIVE'
              AND u.unit_id IN (SELECT id FROM target_units)
            """;
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params =
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("unitIds", unitIds);
        Integer count = namedParameterJdbcTemplate.queryForObject(sql, params, Integer.class);
        return count != null ? count : 0;
    }

    private void enrollStaffForMandatoryUnits(TrainingProgram p, Set<String> mandatoryUnitIds) {
        List<Course> programCourses = courses.findAll().stream()
                .filter(c -> c.getProgram() != null && c.getProgram().getId().equals(p.getId()) && !"DELETED".equals(c.getStatus()))
                .toList();

        for (Course c : programCourses) {
            for (String uId : mandatoryUnitIds) {
                c.getUnitGrants().put(uId, "MANDATORY");
            }
            courses.save(c);
        }
        if (!programCourses.isEmpty()) {
            List<String> cIds = programCourses.stream().map(Course::getId).toList();
            enrollStaffForCoursesNative(cIds, mandatoryUnitIds);
        }
    }

    private void enrollStaffForCourse(Course course, Set<String> mandatoryUnitIds) {
        if (course != null && course.getId() != null) {
            enrollStaffForCoursesNative(List.of(course.getId()), mandatoryUnitIds);
        }
    }

    private void unenrollUnstartedStaffForCourse(Course course, Set<String> unitIds) {
        if (course != null && course.getId() != null) {
            unenrollUnstartedStaffForCoursesNative(List.of(course.getId()), unitIds);
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
            if (grants.isEmpty() && id == null && p.getUnitGrants() != null && !p.getUnitGrants().isEmpty()) {
                grants.putAll(p.getUnitGrants());
            }
            c.getUnitGrants().clear();
            c.getUnitGrants().putAll(grants);
        } else {
            // Auto-inherit unit grants from parent program if new course
            if (p.getUnitGrants() != null && !p.getUnitGrants().isEmpty()) {
                for (var entry : p.getUnitGrants().entrySet()) {
                    if (!c.getUnitGrants().containsKey(entry.getKey())) {
                        c.getUnitGrants().put(entry.getKey(), entry.getValue());
                    }
                }
            }
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

    public Map<String, Object> getNextCode(String type, String module) {
        String mod = clean(module);
        if (mod == null || mod.isBlank()) {
            return Map.of("code", "", "seq", 0);
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
        return Map.of("code", prefix + nextSeq, "seq", nextSeq, "prefix", prefix);
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

    public Map<String, Object> adminDomainsPaged(String search, int page, int size, String unallocatedForUnitId) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        String unitId = (unallocatedForUnitId != null && !unallocatedForUnitId.isBlank()) ? unallocatedForUnitId.trim() : null;

        if (unitId != null) {
            int pageSize = size > 0 ? size : 10;
            int offset = Math.max(0, page) * pageSize;
            org.springframework.jdbc.core.namedparam.MapSqlParameterSource params =
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("search", query)
                            .addValue("unitId", unitId)
                            .addValue("limit", pageSize)
                            .addValue("offset", offset);

            String whereClause = " WHERE d.status <> 'DELETED' " +
                    (query != null ? " AND (d.name ILIKE CONCAT('%', :search, '%') OR d.code ILIKE CONCAT('%', :search, '%') " +
                                     "      OR EXISTS (SELECT 1 FROM training_programs p WHERE p.domain_id = d.id AND p.status <> 'DELETED' " +
                                     "                 AND (p.name ILIKE CONCAT('%', :search, '%') OR p.code ILIKE CONCAT('%', :search, '%') " +
                                     "                      OR EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                                     "                                 AND (c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%')))))) " : "") +
                    " AND ( " +
                    "   EXISTS ( " +
                    "     SELECT 1 FROM training_programs p WHERE p.domain_id = d.id AND p.status <> 'DELETED' " +
                    "     AND ( " +
                    "       EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                    "               AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId)) " +
                    "       OR (NOT EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED') " +
                    "           AND NOT EXISTS (SELECT 1 FROM program_unit_grants g WHERE g.program_id = p.id AND g.unit_id = :unitId)) " +
                    "     ) " +
                    "   ) " +
                    "   OR ( " +
                    "     NOT EXISTS (SELECT 1 FROM training_programs p WHERE p.domain_id = d.id AND p.status <> 'DELETED') " +
                    "     AND NOT EXISTS (SELECT 1 FROM domain_unit_grants g WHERE g.domain_id = d.id AND g.unit_id = :unitId) " +
                    "   ) " +
                    " ) ";

            String countSql = "SELECT COUNT(*) FROM training_domains d " + whereClause;
            Integer totalCount = namedParameterJdbcTemplate.queryForObject(countSql, params, Integer.class);
            int totalElements = totalCount != null ? totalCount : 0;
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);

            String dataSql = "SELECT d.id, d.code, d.name, d.description, d.status, d.business_module, d.seq_number, " +
                    "  (SELECT COUNT(*) FROM training_programs p WHERE p.domain_id = d.id AND p.status <> 'DELETED' " +
                    "   AND ( " +
                    "     EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                    "             AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId)) " +
                    "     OR (NOT EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED') " +
                    "         AND NOT EXISTS (SELECT 1 FROM program_unit_grants g WHERE g.program_id = p.id AND g.unit_id = :unitId)) " +
                    "   )) AS programs_count " +
                    "FROM training_domains d " + whereClause +
                    "ORDER BY d.code ASC, d.name ASC LIMIT :limit OFFSET :offset";

            List<Map<String, Object>> items = namedParameterJdbcTemplate.query(dataSql, params, (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("code", rs.getString("code"));
                m.put("name", rs.getString("name"));
                m.put("description", rs.getString("description"));
                m.put("status", rs.getString("status"));
                m.put("businessModule", rs.getString("business_module"));
                m.put("seqNumber", rs.getInt("seq_number"));
                m.put("programsCount", rs.getLong("programs_count"));
                m.put("unitIds", Collections.emptyList());
                m.put("mandatoryUnitIds", Collections.emptyList());
                m.put("optionalUnitIds", Collections.emptyList());
                m.put("allowedActions", List.of("VIEW", "EDIT"));
                return m;
            });

            Map<String, Object> res = new HashMap<>();
            res.put("items", items);
            res.put("page", page);
            res.put("size", pageSize);
            res.put("totalElements", totalElements);
            res.put("totalPages", Math.max(1, totalPages));
            res.put("hasNext", (page + 1) < totalPages);
            return res;
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<TrainingDomain> result = domains.findByFilter(query, pageable);
        List<Map<String, Object>> items = result.getContent().stream()
                .map(d -> {
                    DomainDto dto = this.domainDto(d);
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", dto.id());
                    m.put("code", dto.code());
                    m.put("name", dto.name());
                    m.put("description", dto.description());
                    m.put("status", dto.status());
                    m.put("allowedActions", dto.allowedActions());
                    m.put("businessModule", dto.businessModule());
                    m.put("seqNumber", dto.seqNumber());
                    m.put("unitIds", dto.unitIds());
                    m.put("mandatoryUnitIds", dto.mandatoryUnitIds());
                    m.put("optionalUnitIds", dto.optionalUnitIds());
                    m.put("programsCount", programs.countByDomainIdAndStatusNot(d.getId(), "DELETED"));
                    return m;
                })
                .toList();

        Map<String, Object> res = new HashMap<>();
        res.put("items", items);
        res.put("page", result.getNumber());
        res.put("size", result.getSize());
        res.put("totalElements", result.getTotalElements());
        res.put("totalPages", result.getTotalPages());
        res.put("hasNext", result.hasNext());
        return res;
    }

    public Map<String, Object> adminCoursesByProgramPaged(String programId, String search, int page, int size, String unallocatedForUnitId) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        String unitId = (unallocatedForUnitId != null && !unallocatedForUnitId.isBlank()) ? unallocatedForUnitId.trim() : null;

        if (unitId != null) {
            int pageSize = size > 0 ? size : 10;
            int offset = Math.max(0, page) * pageSize;
            org.springframework.jdbc.core.namedparam.MapSqlParameterSource params =
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("programId", programId)
                            .addValue("search", query)
                            .addValue("unitId", unitId)
                            .addValue("limit", pageSize)
                            .addValue("offset", offset);

            String whereClause = " WHERE c.program_id = :programId AND c.status <> 'DELETED' " +
                    (query != null ? " AND (c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%')) " : "") +
                    " AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId) ";

            String countSql = "SELECT COUNT(*) FROM courses c " + whereClause;
            Integer totalCount = namedParameterJdbcTemplate.queryForObject(countSql, params, Integer.class);
            int totalElements = totalCount != null ? totalCount : 0;
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);

            String dataSql = "SELECT c.id, c.code, c.name, c.description, c.status, c.business_module, c.level, c.seq_number, c.program_id, " +
                    "  p.name AS program_name, p.domain_id, d.name AS domain_name " +
                    "FROM courses c " +
                    "LEFT JOIN training_programs p ON p.id = c.program_id " +
                    "LEFT JOIN training_domains d ON d.id = p.domain_id " +
                    whereClause +
                    "ORDER BY c.code ASC, c.name ASC LIMIT :limit OFFSET :offset";

            List<CourseDto> dtos = namedParameterJdbcTemplate.query(dataSql, params, (rs, rowNum) -> {
                CourseDto.CourseDtoBuilder b = CourseDto.builder();
                b.id(rs.getString("id"));
                b.code(rs.getString("code"));
                b.name(rs.getString("name"));
                b.description(rs.getString("description"));
                b.status(rs.getString("status"));
                b.businessModule(rs.getString("business_module"));
                b.level(rs.getString("level"));
                b.seqNumber(rs.getInt("seq_number"));
                b.programId(rs.getString("program_id"));
                b.programName(rs.getString("program_name"));
                b.domainId(rs.getString("domain_id"));
                b.domainName(rs.getString("domain_name"));
                b.unitIds(Collections.emptySet());
                b.mandatoryUnitIds(Collections.emptySet());
                b.optionalUnitIds(Collections.emptySet());
                b.allowedActions(java.util.Set.of("VIEW", "EDIT"));
                return b.build();
            });

            Map<String, Object> res = new HashMap<>();
            res.put("items", dtos);
            res.put("page", page);
            res.put("size", pageSize);
            res.put("totalElements", totalElements);
            res.put("totalPages", Math.max(1, totalPages));
            res.put("hasNext", (page + 1) < totalPages);
            return res;
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<Course> result = courses.findByProgramIdWithFilter(programId, query, pageable);
        List<CourseDto> dtos = result.getContent().stream()
                .map(this::mapToAdminCourseDto)
                .toList();

        Map<String, Object> res = new HashMap<>();
        res.put("items", dtos);
        res.put("page", result.getNumber());
        res.put("size", result.getSize());
        res.put("totalElements", result.getTotalElements());
        res.put("totalPages", result.getTotalPages());
        res.put("hasNext", result.hasNext());
        return res;
    }

    public Map<String, Object> adminProgramsPaged(String domainId, String search, int page, int size, String unallocatedForUnitId) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        String dId = (domainId != null && !domainId.isBlank()) ? domainId.trim() : null;
        String unitId = (unallocatedForUnitId != null && !unallocatedForUnitId.isBlank()) ? unallocatedForUnitId.trim() : null;

        if (unitId != null) {
            int pageSize = size > 0 ? size : 10;
            int offset = Math.max(0, page) * pageSize;
            org.springframework.jdbc.core.namedparam.MapSqlParameterSource params =
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("domainId", dId)
                            .addValue("search", query)
                            .addValue("unitId", unitId)
                            .addValue("limit", pageSize)
                            .addValue("offset", offset);

            String whereClause = " WHERE p.status <> 'DELETED' " +
                    (dId != null ? " AND p.domain_id = :domainId " : "") +
                    (query != null ? " AND (p.name ILIKE CONCAT('%', :search, '%') OR p.code ILIKE CONCAT('%', :search, '%') " +
                                     "      OR EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                                     "                 AND (c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%')))) " : "") +
                    " AND ( " +
                    "   EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                    "           AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId)) " +
                    "   OR (NOT EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED') " +
                    "       AND NOT EXISTS (SELECT 1 FROM program_unit_grants g WHERE g.program_id = p.id AND g.unit_id = :unitId)) " +
                    " ) ";

            String countSql = "SELECT COUNT(*) FROM training_programs p " + whereClause;
            Integer totalCount = namedParameterJdbcTemplate.queryForObject(countSql, params, Integer.class);
            int totalElements = totalCount != null ? totalCount : 0;
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);

            String dataSql = "SELECT p.id, p.domain_id, p.code, p.name, p.description, p.status, p.business_module, p.level, p.seq_number, " +
                    "  (SELECT COUNT(*) FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                    "   AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId)) AS courses_count " +
                    "FROM training_programs p " + whereClause +
                    "ORDER BY p.code ASC, p.name ASC LIMIT :limit OFFSET :offset";

            List<Map<String, Object>> dtos = namedParameterJdbcTemplate.query(dataSql, params, (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("domainId", rs.getString("domain_id"));
                m.put("code", rs.getString("code"));
                m.put("name", rs.getString("name"));
                m.put("description", rs.getString("description"));
                m.put("status", rs.getString("status"));
                m.put("businessModule", rs.getString("business_module"));
                m.put("level", rs.getString("level"));
                m.put("seqNumber", rs.getInt("seq_number"));
                m.put("coursesCount", rs.getLong("courses_count"));
                m.put("unitIds", Collections.emptyList());
                m.put("mandatoryUnitIds", Collections.emptyList());
                m.put("optionalUnitIds", Collections.emptyList());
                m.put("allowedActions", List.of("VIEW", "EDIT"));
                return m;
            });

            Map<String, Object> res = new HashMap<>();
            res.put("items", dtos);
            res.put("page", page);
            res.put("size", pageSize);
            res.put("totalElements", totalElements);
            res.put("totalPages", Math.max(1, totalPages));
            res.put("hasNext", (page + 1) < totalPages);
            return res;
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<TrainingProgram> result = programs.findByDomainIdWithFilter(dId, query, pageable);
        List<Map<String, Object>> dtos = result.getContent().stream()
                .map(p -> {
                    ProgramDto dto = this.programDto(p);
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", dto.id());
                    m.put("domainId", dto.domainId());
                    m.put("code", dto.code());
                    m.put("name", dto.name());
                    m.put("description", dto.description());
                    m.put("status", dto.status());
                    m.put("unitIds", dto.unitIds());
                    m.put("allowedActions", dto.allowedActions());
                    m.put("businessModule", dto.businessModule());
                    m.put("level", dto.level());
                    m.put("mandatoryUnitIds", dto.mandatoryUnitIds());
                    m.put("optionalUnitIds", dto.optionalUnitIds());
                    m.put("autoEnrollStaff", dto.autoEnrollStaff());
                    m.put("seqNumber", dto.seqNumber());
                    m.put("coursesCount", courses.countByProgramIdAndStatusNot(p.getId(), "DELETED"));
                    return m;
                })
                .toList();

        Map<String, Object> res = new HashMap<>();
        res.put("items", dtos);
        res.put("page", result.getNumber());
        res.put("size", result.getSize());
        res.put("totalElements", result.getTotalElements());
        res.put("totalPages", result.getTotalPages());
        res.put("hasNext", result.hasNext());
        return res;
    }

    public List<Map<String, Object>> getUnitAllocatedItems(String unitId) {
        String sql = """
            SELECT 'domain' AS category_type, dug.domain_id AS target_id, dug.grant_type, d.code, d.name, NULL AS parent_id
            FROM domain_unit_grants dug
            JOIN training_domains d ON d.id = dug.domain_id
            WHERE dug.unit_id = :unitId AND d.status <> 'DELETED'
            UNION ALL
            SELECT 'program' AS category_type, pug.program_id AS target_id, pug.grant_type, p.code, p.name, p.domain_id AS parent_id
            FROM program_unit_grants pug
            JOIN training_programs p ON p.id = pug.program_id
            WHERE pug.unit_id = :unitId AND p.status <> 'DELETED'
            UNION ALL
            SELECT 'course' AS category_type, cug.course_id AS target_id, cug.grant_type, c.code, c.name, c.program_id AS parent_id
            FROM course_unit_grants cug
            JOIN courses c ON c.id = cug.course_id
            WHERE cug.unit_id = :unitId AND c.status <> 'DELETED'
        """;
        return namedParameterJdbcTemplate.query(sql, Map.of("unitId", unitId), (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("categoryType", rs.getString("category_type"));
            map.put("targetId", rs.getString("target_id"));
            map.put("grantType", rs.getString("grant_type"));
            map.put("code", rs.getString("code"));
            map.put("name", rs.getString("name"));
            map.put("parentId", rs.getString("parent_id"));
            return map;
        });
    }

    public void allocateCoursesToUnit(String unitId, String grantType, String programId, boolean allInProgram, List<String> courseIds) {
        assignUnitAllocation(unitId, "course", grantType, null, programId, allInProgram, courseIds);
    }

    public Map<String, Object> assignUnitAllocation(
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
        if (!com.bidv.coretraining.security.AccessPolicy.activeUnit(unit)) {
            throw new BadRequestException("Đơn vị " + unit.getName() + " đã ngừng hoạt động");
        }

        String normalizedGrant = "MANDATORY".equalsIgnoreCase(grantType) ? "MANDATORY" : "OPTIONAL";
        String type = categoryType == null ? "program" : categoryType.trim().toLowerCase();
        List<Course> targetCourses = new ArrayList<>();

        if ("domain".equals(type)) {
            TrainingDomain d = domains.findById(required(domainId, "Lĩnh vực đào tạo"))
                    .orElseThrow(() -> new ResourceNotFoundException("Domain", "id", domainId));
            d.getUnits().add(unit);
            if (d.getUnitGrants() == null) d.setUnitGrants(new HashMap<>());
            d.getUnitGrants().put(unit.getId(), normalizedGrant);
            domains.save(d);

            List<TrainingProgram> domainPrograms = programs.findAll().stream()
                    .filter(p -> p.getDomain() != null && p.getDomain().getId().equals(d.getId()) && !"DELETED".equals(p.getStatus()))
                    .toList();
            for (TrainingProgram p : domainPrograms) {
                p.getUnits().add(unit);
                if (p.getUnitGrants() == null) p.setUnitGrants(new HashMap<>());
                p.getUnitGrants().put(unit.getId(), normalizedGrant);
                programs.save(p);
                targetCourses.addAll(courses.findByProgramIdAndStatusNot(p.getId(), "DELETED"));
            }
        } else if ("program".equals(type)) {
            TrainingProgram p = programs.findById(required(programId, "Chương trình đào tạo"))
                    .orElseThrow(() -> new ResourceNotFoundException("Program", "id", programId));
            p.getUnits().add(unit);
            if (p.getUnitGrants() == null) p.setUnitGrants(new HashMap<>());
            p.getUnitGrants().put(unit.getId(), normalizedGrant);
            programs.save(p);
            targetCourses.addAll(courses.findByProgramIdAndStatusNot(p.getId(), "DELETED"));
        } else if ("course".equals(type)) {
            if (allInProgram) {
                String pId = required(programId, "Chương trình đào tạo");
                TrainingProgram p = programs.findById(pId)
                        .orElseThrow(() -> new ResourceNotFoundException("Program", "id", pId));
                p.getUnits().add(unit);
                if (p.getUnitGrants() == null) p.setUnitGrants(new HashMap<>());
                p.getUnitGrants().put(unit.getId(), normalizedGrant);
                programs.save(p);
                targetCourses.addAll(courses.findByProgramIdAndStatusNot(pId, "DELETED"));
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
        int unitUsersCount = countActiveStaffInUnitsNative(Set.of(unit.getId()));
        int enrolledUsersCount = 0;

        if ("MANDATORY".equals(normalizedGrant)) {
            // Bắt buộc: Tự động ghi danh tức thì toàn bộ nhân sự phòng ban (và phòng ban con) qua 1 câu Native SQL duy nhất
            if (!targetCourseIds.isEmpty()) {
                enrollStaffForCoursesNative(targetCourseIds, Set.of(unit.getId()));
                enrolledUsersCount = unitUsersCount;
            }
        } else {
            // Tự chọn (OPTIONAL): Rút đăng ký những người chưa bắt đầu học qua 1 câu Native SQL duy nhất
            if (!targetCourseIds.isEmpty()) {
                unenrollUnstartedStaffForCoursesNative(targetCourseIds, Set.of(unit.getId()));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("unitId", unit.getId());
        result.put("unitName", unit.getName());
        result.put("categoryType", type);
        result.put("grantType", normalizedGrant);
        result.put("allocatedCoursesCount", targetCourses.size());
        result.put("unitUsersCount", unitUsersCount);
        result.put("enrolledUsersCount", enrolledUsersCount);
        return result;
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> batchAssignUnitAllocation(
            String unitId,
            List<com.bidv.coretraining.controller.TrainingStructureController.BatchAllocationItem> items) {
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
        Set<String> allAffectedCourseIds = new HashSet<>();

        int domainsCount = 0;
        int programsCount = 0;
        int coursesCount = 0;

        List<com.bidv.coretraining.controller.TrainingStructureController.BatchAllocationItem> sortedItems = items.stream()
                .filter(i -> i != null && i.categoryType() != null && i.targetId() != null)
                .sorted(Comparator.comparingInt(i -> {
                    String t = i.categoryType().trim().toLowerCase();
                    if ("domain".equals(t)) return 1;
                    if ("program".equals(t)) return 2;
                    return 3;
                }))
                .toList();

        for (com.bidv.coretraining.controller.TrainingStructureController.BatchAllocationItem item : sortedItems) {
            String type = item.categoryType().trim().toLowerCase();
            String grant = "MANDATORY".equalsIgnoreCase(item.grantType()) ? "MANDATORY" : "OPTIONAL";
            String targetId = item.targetId().trim();
            boolean onlyUnallocated = item.onlyUnallocated() == null || item.onlyUnallocated();
            List<String> excludeIds = (item.excludeIds() != null) ? item.excludeIds() : Collections.emptyList();

            org.springframework.jdbc.core.namedparam.MapSqlParameterSource p =
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("unitId", unit.getId())
                            .addValue("targetId", targetId)
                            .addValue("grant", grant)
                            .addValue("excludeIds", excludeIds.isEmpty() ? List.of("__NONE__") : excludeIds);

            if ("domain".equals(type)) {
                namedParameterJdbcTemplate.update(
                        "INSERT INTO domain_unit_grants (domain_id, unit_id, grant_type) " +
                        "VALUES (:targetId, :unitId, :grant) " +
                        "ON CONFLICT (domain_id, unit_id) DO UPDATE SET grant_type = EXCLUDED.grant_type", p);
                namedParameterJdbcTemplate.update(
                        "INSERT INTO training_domain_units (domain_id, unit_id) " +
                        "VALUES (:targetId, :unitId) ON CONFLICT DO NOTHING", p);
                domainsCount++;

                String progSql = onlyUnallocated
                        ? "INSERT INTO program_unit_grants (program_id, unit_id, grant_type) " +
                          "SELECT p.id, :unitId, :grant FROM training_programs p " +
                          "WHERE p.domain_id = :targetId AND p.status <> 'DELETED' " +
                          "ON CONFLICT (program_id, unit_id) DO NOTHING"
                        : "INSERT INTO program_unit_grants (program_id, unit_id, grant_type) " +
                          "SELECT p.id, :unitId, :grant FROM training_programs p " +
                          "WHERE p.domain_id = :targetId AND p.status <> 'DELETED' " +
                          "ON CONFLICT (program_id, unit_id) DO UPDATE SET grant_type = EXCLUDED.grant_type";
                namedParameterJdbcTemplate.update(progSql, p);
                namedParameterJdbcTemplate.update(
                        "INSERT INTO training_program_units (program_id, unit_id) " +
                        "SELECT p.id, :unitId FROM training_programs p WHERE p.domain_id = :targetId AND p.status <> 'DELETED' " +
                        "ON CONFLICT DO NOTHING", p);

                String findCoursesSql = onlyUnallocated
                        ? "SELECT c.id FROM courses c JOIN training_programs p ON c.program_id = p.id " +
                          "WHERE p.domain_id = :targetId AND c.status <> 'DELETED' AND c.id NOT IN (:excludeIds) " +
                          "AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId)"
                        : "SELECT c.id FROM courses c JOIN training_programs p ON c.program_id = p.id " +
                          "WHERE p.domain_id = :targetId AND c.status <> 'DELETED' AND c.id NOT IN (:excludeIds)";
                List<String> curCourseIds = namedParameterJdbcTemplate.queryForList(findCoursesSql, p, String.class);

                if (!curCourseIds.isEmpty()) {
                    org.springframework.jdbc.core.namedparam.MapSqlParameterSource cp =
                            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                                    .addValue("unitId", unit.getId())
                                    .addValue("grant", grant)
                                    .addValue("cIds", curCourseIds);
                    namedParameterJdbcTemplate.update(
                            "INSERT INTO course_unit_grants (course_id, unit_id, grant_type) " +
                            "SELECT c.id, :unitId, :grant FROM courses c WHERE c.id IN (:cIds) " +
                            "ON CONFLICT (course_id, unit_id) DO UPDATE SET grant_type = EXCLUDED.grant_type", cp);
                    allAffectedCourseIds.addAll(curCourseIds);
                    if ("MANDATORY".equals(grant)) {
                        mandatoryCourseIds.addAll(curCourseIds);
                        optionalCourseIds.removeAll(curCourseIds);
                    } else {
                        optionalCourseIds.addAll(curCourseIds);
                        mandatoryCourseIds.removeAll(curCourseIds);
                    }
                }
            } else if ("program".equals(type)) {
                namedParameterJdbcTemplate.update(
                        "INSERT INTO program_unit_grants (program_id, unit_id, grant_type) " +
                        "VALUES (:targetId, :unitId, :grant) " +
                        "ON CONFLICT (program_id, unit_id) DO UPDATE SET grant_type = EXCLUDED.grant_type", p);
                namedParameterJdbcTemplate.update(
                        "INSERT INTO training_program_units (program_id, unit_id) " +
                        "VALUES (:targetId, :unitId) ON CONFLICT DO NOTHING", p);
                namedParameterJdbcTemplate.update(
                        "INSERT INTO training_domain_units (domain_id, unit_id) " +
                        "SELECT p.domain_id, :unitId FROM training_programs p WHERE p.id = :targetId " +
                        "ON CONFLICT DO NOTHING", p);
                programsCount++;

                String findCoursesSql = onlyUnallocated
                        ? "SELECT c.id FROM courses c WHERE c.program_id = :targetId AND c.status <> 'DELETED' " +
                          "AND c.id NOT IN (:excludeIds) " +
                          "AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId)"
                        : "SELECT c.id FROM courses c WHERE c.program_id = :targetId AND c.status <> 'DELETED' " +
                          "AND c.id NOT IN (:excludeIds)";
                List<String> curCourseIds = namedParameterJdbcTemplate.queryForList(findCoursesSql, p, String.class);

                if (!curCourseIds.isEmpty()) {
                    org.springframework.jdbc.core.namedparam.MapSqlParameterSource cp =
                            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                                    .addValue("unitId", unit.getId())
                                    .addValue("grant", grant)
                                    .addValue("cIds", curCourseIds);
                    namedParameterJdbcTemplate.update(
                            "INSERT INTO course_unit_grants (course_id, unit_id, grant_type) " +
                            "SELECT c.id, :unitId, :grant FROM courses c WHERE c.id IN (:cIds) " +
                            "ON CONFLICT (course_id, unit_id) DO UPDATE SET grant_type = EXCLUDED.grant_type", cp);
                    allAffectedCourseIds.addAll(curCourseIds);
                    if ("MANDATORY".equals(grant)) {
                        mandatoryCourseIds.addAll(curCourseIds);
                        optionalCourseIds.removeAll(curCourseIds);
                    } else {
                        optionalCourseIds.addAll(curCourseIds);
                        mandatoryCourseIds.removeAll(curCourseIds);
                    }
                }
            } else if ("course".equals(type)) {
                namedParameterJdbcTemplate.update(
                        "INSERT INTO course_unit_grants (course_id, unit_id, grant_type) " +
                        "VALUES (:targetId, :unitId, :grant) " +
                        "ON CONFLICT (course_id, unit_id) DO UPDATE SET grant_type = EXCLUDED.grant_type", p);
                namedParameterJdbcTemplate.update(
                        "INSERT INTO training_program_units (program_id, unit_id) " +
                        "SELECT c.program_id, :unitId FROM courses c WHERE c.id = :targetId " +
                        "ON CONFLICT DO NOTHING", p);
                namedParameterJdbcTemplate.update(
                        "INSERT INTO training_domain_units (domain_id, unit_id) " +
                        "SELECT p.domain_id, :unitId FROM courses c JOIN training_programs p ON c.program_id = p.id " +
                        "WHERE c.id = :targetId ON CONFLICT DO NOTHING", p);

                coursesCount++;
                allAffectedCourseIds.add(targetId);
                if ("MANDATORY".equals(grant)) {
                    mandatoryCourseIds.add(targetId);
                    optionalCourseIds.remove(targetId);
                } else {
                    optionalCourseIds.add(targetId);
                    mandatoryCourseIds.remove(targetId);
                }
            }
        }

        if (!allAffectedCourseIds.isEmpty()) {
            org.springframework.jdbc.core.namedparam.MapSqlParameterSource cp =
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("cIds", allAffectedCourseIds);
            namedParameterJdbcTemplate.update(
                    "UPDATE courses SET status = 'PUBLISHED' WHERE id IN (:cIds) AND status = 'DRAFT'", cp);
            namedParameterJdbcTemplate.update(
                    "UPDATE chapters SET status = 'PUBLISHED' WHERE course_id IN (:cIds) AND status = 'DRAFT'", cp);
            namedParameterJdbcTemplate.update(
                    "UPDATE lessons SET status = 'PUBLISHED' " +
                    "WHERE chapter_id IN (SELECT id FROM chapters WHERE course_id IN (:cIds)) " +
                    "AND (status = 'DRAFT' OR status IS NULL)", cp);
        }

        int unitUsersCount = countActiveStaffInUnitsNative(Set.of(unit.getId()));
        int enrolledUsersCount = 0;
        if (!mandatoryCourseIds.isEmpty()) {
            enrollStaffForCoursesNative(new ArrayList<>(mandatoryCourseIds), Set.of(unit.getId()));
            enrolledUsersCount = unitUsersCount;
        }
        if (!optionalCourseIds.isEmpty()) {
            unenrollUnstartedStaffForCoursesNative(new ArrayList<>(optionalCourseIds), Set.of(unit.getId()));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("unitId", unit.getId());
        result.put("unitName", unit.getName());
        result.put("domainsCount", domainsCount);
        result.put("programsCount", programsCount);
        result.put("coursesCount", coursesCount);
        result.put("totalAffectedCourses", allAffectedCourseIds.size());
        result.put("unitUsersCount", unitUsersCount);
        result.put("enrolledUsersCount", enrolledUsersCount);
        result.put("status", "SUCCESS");
        return result;
    }

    public Map<String, Object> batchSyncUnitAllocations(
            String unitId,
            List<com.bidv.coretraining.controller.TrainingStructureController.BatchChangeItem> changes) {
        authorization.permission("CATALOG_MANAGE");
        OrganizationUnit unit = authorization.unit(required(unitId, "Đơn vị áp dụng"));
        authorization.scope(unit);
        if (!com.bidv.coretraining.security.AccessPolicy.activeUnit(unit)) {
            throw new BadRequestException("Đơn vị " + unit.getName() + " đã ngừng hoạt động");
        }

        if (changes == null || changes.isEmpty()) {
            return Map.of("status", "SUCCESS", "message", "Không có thay đổi nào cần lưu");
        }

        int updatedCount = 0;
        int removedCount = 0;

        for (com.bidv.coretraining.controller.TrainingStructureController.BatchChangeItem item : changes) {
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

        Map<String, Object> result = new HashMap<>();
        result.put("unitId", unit.getId());
        result.put("unitName", unit.getName());
        result.put("updatedCount", updatedCount);
        result.put("removedCount", removedCount);
        result.put("totalChanges", updatedCount + removedCount);
        result.put("status", "SUCCESS");
        return result;
    }

    public Map<String, Object> toggleUnitAllocationGrantType(
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

    public Map<String, Object> removeUnitAllocation(
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
            d.getUnits().removeIf(u -> u.getId().equals(unitId));
            if (d.getUnitGrants() != null) d.getUnitGrants().remove(unitId);
            domains.save(d);

            List<TrainingProgram> domainPrograms = programs.findAll().stream()
                    .filter(p -> p.getDomain() != null && p.getDomain().getId().equals(d.getId()))
                    .toList();
            for (TrainingProgram p : domainPrograms) {
                p.getUnits().removeIf(u -> u.getId().equals(unitId));
                if (p.getUnitGrants() != null) p.getUnitGrants().remove(unitId);
                programs.save(p);
                affectedCourses.addAll(courses.findByProgramIdAndStatusNot(p.getId(), "DELETED"));
            }
        } else if ("program".equals(type)) {
            TrainingProgram p = programs.findById(required(targetId, "Chương trình"))
                    .orElseThrow(() -> new ResourceNotFoundException("Program", "id", targetId));
            p.getUnits().removeIf(u -> u.getId().equals(unitId));
            if (p.getUnitGrants() != null) p.getUnitGrants().remove(unitId);
            programs.save(p);
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
            unenrollUnstartedStaffForCoursesNative(cIds, Set.of(unitId));
        }

        return Map.of(
                "unitId", unitId,
                "removedCoursesCount", affectedCourses.size()
        );
    }

    public List<CourseDto> adminCoursesByUnit(String unitId) {
        // Collect courses explicitly assigned to unit OR whose program/domain is assigned to unit
        Set<String> courseIds = new HashSet<>();
        for (Course c : courses.findByUnitGrant(unitId)) {
            courseIds.add(c.getId());
        }
        for (Course c : courses.findAll()) {
            if ("DELETED".equals(c.getStatus())) continue;
            boolean hasCourseGrant = c.getUnitGrants() != null && c.getUnitGrants().containsKey(unitId);
            boolean hasProgramGrant = c.getProgram() != null && (
                    (c.getProgram().getUnitGrants() != null && c.getProgram().getUnitGrants().containsKey(unitId)) ||
                    (c.getProgram().getUnits() != null && c.getProgram().getUnits().stream().anyMatch(u -> unitId.equals(u.getId())))
            );
            boolean hasDomainGrant = c.getProgram() != null && c.getProgram().getDomain() != null && (
                    (c.getProgram().getDomain().getUnitGrants() != null && c.getProgram().getDomain().getUnitGrants().containsKey(unitId)) ||
                    (c.getProgram().getDomain().getUnits() != null && c.getProgram().getDomain().getUnits().stream().anyMatch(u -> unitId.equals(u.getId())))
            );
            if (hasCourseGrant || hasProgramGrant || hasDomainGrant) {
                courseIds.add(c.getId());
            }
        }
        return courses.findAllById(courseIds).stream()
                .filter(c -> !"DELETED".equals(c.getStatus()))
                .map(this::mapToAdminCourseDto)
                .toList();
    }

    private CourseDto mapToAdminCourseDto(Course c) {
        CourseDto dto = mapper.toCourseDto(c);
        dto.setCanEdit(com.bidv.coretraining.security.AccessPolicy.canEdit(authorization.current(), c));
        dto.setAllowedActions(
                com.bidv.coretraining.security.AccessPolicy.courseActions(authorization.current(), c));
        dto.setAdminActions(java.util.stream.Stream.of("courses", "exams", "documents")
                .collect(java.util.stream.Collectors.toMap(p -> p,
                        p -> java.util.stream.Stream.of("read", "create", "update", "delete")
                                 .filter(a -> com.bidv.coretraining.security.AccessPolicy
                                        .allowsCourse(authorization.current(), p + ":" + a, c))
                                .collect(java.util.stream.Collectors.toSet()))));
        if (!com.bidv.coretraining.security.AccessPolicy.canReadCourse(authorization.current(), c))
            dto.getLessons().forEach(TrainingStructureService::outlineOnly);

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
        if (c.getProgram() != null) {
            if (c.getProgram().getUnitGrants() != null) {
                for (var entry : c.getProgram().getUnitGrants().entrySet()) {
                    if (!mandatory.contains(entry.getKey()) && !optional.contains(entry.getKey())) {
                        if ("MANDATORY".equalsIgnoreCase(entry.getValue())) {
                            mandatory.add(entry.getKey());
                        } else {
                            optional.add(entry.getKey());
                        }
                    }
                }
            }
            if (c.getProgram().getUnits() != null) {
                for (OrganizationUnit u : c.getProgram().getUnits()) {
                    if (!mandatory.contains(u.getId()) && !optional.contains(u.getId())) {
                        optional.add(u.getId());
                    }
                }
            }
            if (c.getProgram().getDomain() != null) {
                TrainingDomain d = c.getProgram().getDomain();
                if (d.getUnitGrants() != null) {
                    for (var entry : d.getUnitGrants().entrySet()) {
                        if (!mandatory.contains(entry.getKey()) && !optional.contains(entry.getKey())) {
                            if ("MANDATORY".equalsIgnoreCase(entry.getValue())) {
                                mandatory.add(entry.getKey());
                            } else {
                                optional.add(entry.getKey());
                            }
                        }
                    }
                }
                if (d.getUnits() != null) {
                    for (OrganizationUnit u : d.getUnits()) {
                        if (!mandatory.contains(u.getId()) && !optional.contains(u.getId())) {
                            optional.add(u.getId());
                        }
                    }
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
        boolean admin = user == null || com.bidv.coretraining.security.AccessPolicy.canReadCourse(user, c.getCourse());
        List<Lesson> list = (admin ? c.getLessons() : LearningPolicy.publishedLessons(c))
                .stream().filter(l -> !"DELETED".equals(l.getStatus())).toList();
        List<LessonDto> lessonDtos = mapper.toLessonDtoList(list);
        if (!admin)
            lessonDtos.forEach(TrainingStructureService::outlineOnly);
        return new ChapterDto(c.getId(), c.getCourse().getId(), c.getCode(), c.getTitle(), c.getDescription(),
                c.getChapterOrder(), c.getStatus(), c.getVersion(), lessonDtos,
                !admin && !LearningPolicy.chapterUnlocked(user, c),
                user != null && LearningPolicy.chapterCompleted(user, c));
    }

    public static void outlineOnly(LessonDto dto) {
        dto.setVideoUrl(null);
        dto.setTextBody(null);
        dto.setDocuments(List.of());
        dto.setProcedureSteps(List.of());
        dto.setComments(List.of());
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
        if (users.findAll().stream().anyMatch(
                u -> u.getCompletedLessons().stream().anyMatch(l -> ids.contains(l.getId())) || u.getQuizScores()
                        .stream().anyMatch(q -> q.getLesson() != null && ids.contains(q.getLesson().getId()))))
            throw new BadRequestException("Nội dung đã có lịch sử học. Hãy lưu trữ khóa thay vì xóa");
    }

    public void deleteDomain(String id) {
        authorization.permission("CATALOG_MANAGE");
        TrainingDomain d = domains.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Domain", "id", id));
        String suffix = "_DEL_" + (System.currentTimeMillis() % 100000);
        int negBase = -Math.abs((int) (System.currentTimeMillis() % 900000) + 1000);

        Map<String, Object> params = Map.of(
                "domainId", id,
                "suffix", suffix,
                "negBase", negBase
        );

        // 1. Un-enroll unstarted staff from all child courses
        List<String> childCourseIds = namedParameterJdbcTemplate.queryForList(
                "SELECT c.id FROM courses c JOIN training_programs p ON c.program_id = p.id WHERE p.domain_id = :domainId",
                params, String.class);
        if (!childCourseIds.isEmpty()) {
            unenrollUnstartedStaffForCoursesNative(new HashSet<>(childCourseIds));
        }

        // 2. Bulk soft delete lessons
        namedParameterJdbcTemplate.update(
                "WITH numbered AS (" +
                "  SELECT l.id, ROW_NUMBER() OVER () as rn " +
                "  FROM lessons l JOIN courses c ON l.course_id = c.id JOIN training_programs p ON c.program_id = p.id " +
                "  WHERE p.domain_id = :domainId AND l.status <> 'DELETED'" +
                ") " +
                "UPDATE lessons l SET status = 'DELETED', lesson_order = :negBase - numbered.rn " +
                "FROM numbered WHERE l.id = numbered.id", params);

        // 3. Bulk soft delete chapters
        namedParameterJdbcTemplate.update(
                "WITH numbered AS (" +
                "  SELECT ch.id, ROW_NUMBER() OVER () as rn " +
                "  FROM chapters ch JOIN courses c ON ch.course_id = c.id JOIN training_programs p ON c.program_id = p.id " +
                "  WHERE p.domain_id = :domainId AND ch.status <> 'DELETED'" +
                ") " +
                "UPDATE chapters ch SET status = 'DELETED', " +
                "code = CASE WHEN ch.code NOT LIKE '%_DEL_%' THEN ch.code || :suffix ELSE ch.code END, " +
                "chapter_order = :negBase - numbered.rn " +
                "FROM numbered WHERE ch.id = numbered.id", params);

        // 4. Bulk soft delete courses
        namedParameterJdbcTemplate.update(
                "UPDATE courses SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE program_id IN (SELECT id FROM training_programs WHERE domain_id = :domainId) AND status <> 'DELETED'", params);

        // 5. Bulk soft delete programs
        namedParameterJdbcTemplate.update(
                "UPDATE training_programs SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE domain_id = :domainId AND status <> 'DELETED'", params);

        // 6. Soft delete domain
        namedParameterJdbcTemplate.update(
                "UPDATE training_domains SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE id = :domainId", params);
    }

    public void deleteProgram(String id) {
        authorization.permission("CATALOG_MANAGE");
        TrainingProgram p = programs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));
        String suffix = "_DEL_" + (System.currentTimeMillis() % 100000);
        int negBase = -Math.abs((int) (System.currentTimeMillis() % 900000) + 1000);

        Map<String, Object> params = Map.of(
                "programId", id,
                "suffix", suffix,
                "negBase", negBase
        );

        // 1. Un-enroll unstarted staff
        List<String> childCourseIds = namedParameterJdbcTemplate.queryForList(
                "SELECT id FROM courses WHERE program_id = :programId",
                params, String.class);
        if (!childCourseIds.isEmpty()) {
            unenrollUnstartedStaffForCoursesNative(new HashSet<>(childCourseIds));
        }

        // 2. Bulk soft delete lessons
        namedParameterJdbcTemplate.update(
                "WITH numbered AS (" +
                "  SELECT l.id, ROW_NUMBER() OVER () as rn " +
                "  FROM lessons l JOIN courses c ON l.course_id = c.id " +
                "  WHERE c.program_id = :programId AND l.status <> 'DELETED'" +
                ") " +
                "UPDATE lessons l SET status = 'DELETED', lesson_order = :negBase - numbered.rn " +
                "FROM numbered WHERE l.id = numbered.id", params);

        // 3. Bulk soft delete chapters
        namedParameterJdbcTemplate.update(
                "WITH numbered AS (" +
                "  SELECT ch.id, ROW_NUMBER() OVER () as rn " +
                "  FROM chapters ch JOIN courses c ON ch.course_id = c.id " +
                "  WHERE c.program_id = :programId AND ch.status <> 'DELETED'" +
                ") " +
                "UPDATE chapters ch SET status = 'DELETED', " +
                "code = CASE WHEN ch.code NOT LIKE '%_DEL_%' THEN ch.code || :suffix ELSE ch.code END, " +
                "chapter_order = :negBase - numbered.rn " +
                "FROM numbered WHERE ch.id = numbered.id", params);

        // 4. Bulk soft delete courses
        namedParameterJdbcTemplate.update(
                "UPDATE courses SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE program_id = :programId AND status <> 'DELETED'", params);

        // 5. Soft delete program
        namedParameterJdbcTemplate.update(
                "UPDATE training_programs SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE id = :programId", params);
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

        Map<String, Object> params = Map.of(
                "courseId", id,
                "suffix", suffix,
                "negBase", negBase
        );

        // 1. Un-enroll unstarted staff
        unenrollUnstartedStaffForCoursesNative(Set.of(id));

        // 2. Bulk soft delete lessons
        namedParameterJdbcTemplate.update(
                "WITH numbered AS (" +
                "  SELECT id, ROW_NUMBER() OVER () as rn " +
                "  FROM lessons " +
                "  WHERE course_id = :courseId AND status <> 'DELETED'" +
                ") " +
                "UPDATE lessons l SET status = 'DELETED', lesson_order = :negBase - numbered.rn " +
                "FROM numbered WHERE l.id = numbered.id", params);

        // 3. Bulk soft delete chapters
        namedParameterJdbcTemplate.update(
                "WITH numbered AS (" +
                "  SELECT id, ROW_NUMBER() OVER () as rn " +
                "  FROM chapters " +
                "  WHERE course_id = :courseId AND status <> 'DELETED'" +
                ") " +
                "UPDATE chapters ch SET status = 'DELETED', " +
                "code = CASE WHEN ch.code NOT LIKE '%_DEL_%' THEN ch.code || :suffix ELSE ch.code END, " +
                "chapter_order = :negBase - numbered.rn " +
                "FROM numbered WHERE ch.id = numbered.id", params);

        // 4. Soft delete course
        namedParameterJdbcTemplate.update(
                "UPDATE courses SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE id = :courseId", params);
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
            if (c.getChapters().isEmpty())
                throw new BadRequestException("Khóa chưa có chương");
            for (Chapter ch : c.getChapters()) {
                if (ch.getLessons().isEmpty())
                    throw new BadRequestException("Chương " + ch.getTitle() + " chưa có bài");
                for (Lesson l : ch.getLessons()) {
                    LessonContentPolicy.requirePublishable(l);
                    l.setStatus("PUBLISHED");
                }
                ch.setStatus("PUBLISHED");
            }
        }
        c.setStatus(status);
        return mapper.toCourseDto(courses.save(c));
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

    public Map<String, Object> getUnitAllocationDirectoryPaged(String search, String type, String status, int page, int size) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        String typeFilter = (type != null && !type.isBlank()) ? type.trim().toUpperCase() : "ALL";
        String statusFilter = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : "ALL";

        int pageSize = size > 0 ? size : 10;
        int offset = Math.max(0, page) * pageSize;

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("search", query);
        params.addValue("limit", pageSize);
        params.addValue("offset", offset);

        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        if (query != null) {
            whereClause.append(" AND u.name ILIKE CONCAT('%', :search, '%') ");
        }
        if ("DEPARTMENT".equals(typeFilter)) {
            whereClause.append(" AND u.type = 'DEPARTMENT' ");
        } else if ("BRANCH".equals(typeFilter)) {
            whereClause.append(" AND u.type <> 'DEPARTMENT' ");
        }
        if ("ALLOCATED".equals(statusFilter)) {
            whereClause.append(" AND (EXISTS (SELECT 1 FROM domain_unit_grants dug WHERE dug.unit_id = u.id) OR " +
                               "      EXISTS (SELECT 1 FROM program_unit_grants pug WHERE pug.unit_id = u.id) OR " +
                               "      EXISTS (SELECT 1 FROM course_unit_grants cug WHERE cug.unit_id = u.id)) ");
        } else if ("UNALLOCATED".equals(statusFilter)) {
            whereClause.append(" AND NOT (EXISTS (SELECT 1 FROM domain_unit_grants dug WHERE dug.unit_id = u.id) OR " +
                               "          EXISTS (SELECT 1 FROM program_unit_grants pug WHERE pug.unit_id = u.id) OR " +
                               "          EXISTS (SELECT 1 FROM course_unit_grants cug WHERE cug.unit_id = u.id)) ");
        }

        String countSql = "SELECT COUNT(*) FROM organization_units u " + whereClause;
        Integer totalCount = namedParameterJdbcTemplate.queryForObject(countSql, params, Integer.class);
        int totalElements = totalCount != null ? totalCount : 0;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        String dataSql = "SELECT u.id, u.name, u.type, u.active, u.parent_id, " +
                "(SELECT COUNT(DISTINCT dug.domain_id) FROM domain_unit_grants dug WHERE dug.unit_id = u.id) AS domain_count, " +
                "(SELECT COUNT(DISTINCT pug.program_id) FROM program_unit_grants pug WHERE pug.unit_id = u.id) AS program_count, " +
                "(SELECT COUNT(DISTINCT cug.course_id) FROM course_unit_grants cug WHERE cug.unit_id = u.id) AS course_count " +
                "FROM organization_units u " + whereClause + " ORDER BY u.name ASC LIMIT :limit OFFSET :offset";

        List<Map<String, Object>> items = namedParameterJdbcTemplate.query(dataSql, params, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rs.getString("id"));
            map.put("name", rs.getString("name"));
            map.put("type", rs.getString("type"));
            map.put("active", rs.getBoolean("active"));
            map.put("parentId", rs.getString("parent_id"));
            int dCount = rs.getInt("domain_count");
            int pCount = rs.getInt("program_count");
            int cCount = rs.getInt("course_count");
            map.put("domainCount", dCount);
            map.put("programCount", pCount);
            map.put("courseCount", cCount);
            map.put("isAllocated", (dCount > 0 || pCount > 0 || cCount > 0));
            return map;
        });

        // Overview stats for the pills
        String statsSql = "SELECT " +
                "COUNT(*) AS total_count, " +
                "COUNT(CASE WHEN u.type = 'DEPARTMENT' THEN 1 END) AS department_count, " +
                "COUNT(CASE WHEN u.type <> 'DEPARTMENT' THEN 1 END) AS branch_count, " +
                "COUNT(CASE WHEN EXISTS (SELECT 1 FROM domain_unit_grants dug WHERE dug.unit_id = u.id) " +
                "             OR EXISTS (SELECT 1 FROM program_unit_grants pug WHERE pug.unit_id = u.id) " +
                "             OR EXISTS (SELECT 1 FROM course_unit_grants cug WHERE cug.unit_id = u.id) THEN 1 END) AS allocated_count " +
                "FROM organization_units u";

        Map<String, Object> stats = namedParameterJdbcTemplate.queryForMap(statsSql, Collections.emptyMap());
        int totalUnits = ((Number) stats.getOrDefault("total_count", 0)).intValue();
        int allocatedUnits = ((Number) stats.getOrDefault("allocated_count", 0)).intValue();
        int branchUnits = ((Number) stats.getOrDefault("branch_count", 0)).intValue();
        int departmentUnits = ((Number) stats.getOrDefault("department_count", 0)).intValue();

        Map<String, Object> res = new HashMap<>();
        res.put("items", items);
        res.put("page", page);
        res.put("size", pageSize);
        res.put("totalElements", totalElements);
        res.put("totalPages", Math.max(1, totalPages));
        res.put("stats", Map.of(
                "totalCount", totalUnits,
                "allocatedCount", allocatedUnits,
                "unallocatedCount", Math.max(0, totalUnits - allocatedUnits),
                "branchCount", branchUnits,
                "departmentCount", departmentUnits
        ));
        return res;
    }

}
