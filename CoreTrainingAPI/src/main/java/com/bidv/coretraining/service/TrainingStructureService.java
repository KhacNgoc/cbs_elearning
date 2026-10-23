package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

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
    private LearnerWorkspaceService workspace;

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
        return com.bidv.coretraining.security.AccessPolicy.has(u, "catalog:update")
                && programs.findAll().stream().filter(p -> p.getDomain().getId().equals(d.getId())).allMatch(
                        p -> com.bidv.coretraining.security.AccessPolicy.allowsProgram(u, "catalog:update", p));
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
            d.setCode(required(request.code(), "Mã lĩnh vực"));
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
            p.setCode(required(request.code(), "Mã chương trình"));
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

    private void enrollStaffForMandatoryUnits(TrainingProgram p, Set<String> mandatoryUnitIds) {
        List<Course> programCourses = courses.findAll().stream()
                .filter(c -> c.getProgram() != null && c.getProgram().getId().equals(p.getId()) && !"DELETED".equals(c.getStatus()))
                .toList();

        for (Course c : programCourses) {
            for (String uId : mandatoryUnitIds) {
                c.getUnitGrants().put(uId, "MANDATORY");
            }
            courses.save(c);
            enrollStaffForCourse(c, mandatoryUnitIds);
        }
    }

    private List<User> findActiveStaffInUnits(Set<String> rootUnitIds) {
        if (rootUnitIds == null || rootUnitIds.isEmpty()) return List.of();
        return users.findAll().stream()
                .filter(User::activeAccount)
                .filter(u -> u.getOrganizationUnit() != null
                        && com.bidv.coretraining.security.AccessPolicy.ancestors(u.getOrganizationUnit())
                                .stream().anyMatch(rootUnitIds::contains))
                .toList();
    }

    private void enrollStaffForCourse(Course course, Set<String> mandatoryUnitIds) {
        List<User> staff = findActiveStaffInUnits(mandatoryUnitIds);
        for (User u : staff) {
            if (!u.getRegisteredCourses().contains(course)) {
                u.getRegisteredCourses().add(course);
                users.save(u);
            }
        }
    }

    private void unenrollUnstartedStaffForCourse(Course course, Set<String> unitIds) {
        List<User> staff = findActiveStaffInUnits(unitIds);
        Set<String> courseLessonIds = course.getChapters().stream()
                .flatMap(ch -> ch.getLessons().stream())
                .map(Lesson::getId)
                .collect(java.util.stream.Collectors.toSet());
        for (User u : staff) {
            if (u.getRegisteredCourses().contains(course)) {
                boolean hasCompletedAnyLesson = u.getCompletedLessons().stream()
                        .anyMatch(l -> courseLessonIds.contains(l.getId()));
                if (!hasCompletedAnyLesson) {
                    u.getRegisteredCourses().remove(course);
                    users.save(u);
                }
            }
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
            c.setCode(required(r.code(), "Mã khóa"));
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

    public Map<String, Object> adminCoursesByProgramPaged(String programId, String search, int page, int size) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
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

    public Map<String, Object> adminProgramsPaged(String domainId, String search, int page, int size) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        String dId = (domainId != null && !domainId.isBlank()) ? domainId.trim() : null;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<TrainingProgram> result = programs.findByDomainIdWithFilter(dId, query, pageable);
        List<ProgramDto> dtos = result.getContent().stream()
                .map(this::programDto)
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

        List<User> staffInUnit = findActiveStaffInUnits(Set.of(unit.getId()));
        int enrolledUsersCount = 0;

        if ("MANDATORY".equals(normalizedGrant)) {
            // Bắt buộc: Tự động đăng ký toàn bộ khóa học cho tất cả nhân sự trong phòng ban (vào thẳng "Khóa học của tôi")
            for (User u : staffInUnit) {
                boolean changed = false;
                for (Course c : targetCourses) {
                    if (!u.getRegisteredCourses().contains(c)) {
                        u.getRegisteredCourses().add(c);
                        changed = true;
                    }
                }
                if (changed) {
                    users.save(u);
                }
                if (!targetCourses.isEmpty()) {
                    enrolledUsersCount++;
                }
            }
        } else {
            // Tự chọn (OPTIONAL): Không tự động đăng ký, để hiển thị ở ngoài ("Khám phá & đăng ký") cho nhân sự tự đăng ký học
            for (Course c : targetCourses) {
                unenrollUnstartedStaffForCourse(c, Set.of(unit.getId()));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("unitId", unit.getId());
        result.put("unitName", unit.getName());
        result.put("categoryType", type);
        result.put("grantType", normalizedGrant);
        result.put("allocatedCoursesCount", targetCourses.size());
        result.put("unitUsersCount", staffInUnit.size());
        result.put("enrolledUsersCount", enrolledUsersCount);
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
            unenrollUnstartedStaffForCourse(c, Set.of(unitId));
        }

        return Map.of(
                "unitId", unitId,
                "removedCoursesCount", affectedCourses.size()
        );
    }

    public List<CourseDto> adminCoursesByUnit(String unitId) {
        return courses.findByUnitGrant(unitId).stream()
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
        } else if (c.getProgram() != null && c.getProgram().getUnitGrants() != null) {
            for (var entry : c.getProgram().getUnitGrants().entrySet()) {
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
        c.setCode(required(r.code(), "Mã chương"));
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
        d.setStatus("DELETED");
        if (d.getCode() != null && !d.getCode().contains("_DEL_")) {
            d.setCode(d.getCode() + suffix);
        }
        domains.saveAndFlush(d);

        List<TrainingProgram> childPrograms = programs.findAll().stream()
                .filter(p -> p.getDomain() != null && id.equals(p.getDomain().getId()) && !"DELETED".equals(p.getStatus()))
                .toList();
        for (TrainingProgram p : childPrograms) {
            deleteProgramInternal(p, suffix);
        }
    }

    public void deleteProgram(String id) {
        authorization.permission("CATALOG_MANAGE");
        TrainingProgram p = programs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));
        String suffix = "_DEL_" + (System.currentTimeMillis() % 100000);
        deleteProgramInternal(p, suffix);
    }

    private void deleteProgramInternal(TrainingProgram p, String suffix) {
        p.setStatus("DELETED");
        if (p.getCode() != null && !p.getCode().contains("_DEL_")) {
            p.setCode(p.getCode() + suffix);
        }
        programs.saveAndFlush(p);

        List<Course> childCourses = courses.findByProgramIdAndStatusNot(p.getId(), "DELETED");
        for (Course c : childCourses) {
            deleteCourseInternal(c, suffix);
        }
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
        deleteCourseInternal(c, suffix);
    }

    private void deleteCourseInternal(Course c, String suffix) {
        c.setStatus("DELETED");
        if (c.getCode() != null && !c.getCode().contains("_DEL_")) {
            c.setCode(c.getCode() + suffix);
        }
        int negBase = -Math.abs((int) (System.currentTimeMillis() % 900000) + 1000);
        int chIdx = 1;
        for (Chapter ch : c.getChapters()) {
            ch.setStatus("DELETED");
            if (ch.getCode() != null && !ch.getCode().contains("_DEL_")) {
                ch.setCode(ch.getCode() + suffix);
            }
            ch.setChapterOrder(negBase - (chIdx++));
            int lIdx = 1;
            for (Lesson l : ch.getLessons()) {
                l.setStatus("DELETED");
                l.setLessonOrder(negBase - (chIdx * 100) - (lIdx++));
            }
        }
        courses.saveAndFlush(c);
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
                    if (l.getContent() == null || (blank(l.getVideoUrl()) && blank(l.getTextBody())
                            && l.getDocuments().isEmpty() && l.getProcedureSteps().isEmpty()))
                        throw new BadRequestException("Bài " + l.getTitle() + " chưa có nội dung");
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

    public OutlineDto outline(String id, String username) {
        Course course = course(id);
        User user = user(username);
        LearningPolicy.assertCourseAccess(user, course);
        List<Chapter> list = com.bidv.coretraining.security.AccessPolicy.canReadCourse(user, course)
                ? course.getChapters()
                : LearningPolicy.publishedChapters(course);
        List<ChapterDto> dto = list.stream().map(ch -> chapterDto(ch, user)).toList();
        String resume = list.stream().flatMap(ch -> LearningPolicy.publishedLessons(ch).stream())
                .filter(l -> !LearningPolicy.completed(user, l) && LearningPolicy.lessonUnlocked(user, l))
                .map(Lesson::getId).findFirst().orElse(null);
        CourseDto courseDto = mapper.toCourseDto(course);
        courseDto.setCanEdit(com.bidv.coretraining.security.AccessPolicy.canReadCourse(user, course));
        if (!com.bidv.coretraining.security.AccessPolicy.canReadCourse(user, course))
            courseDto.getLessons().forEach(TrainingStructureService::outlineOnly);
        var target = workspace.resumeCourse(course, user);
        return new OutlineDto(courseDto, dto, !dto.isEmpty() && dto.stream().allMatch(ChapterDto::completed),
                target == null ? resume : target.lessonId(), target);
    }

    public void requireLesson(String id, String username) {
        LearningPolicy.assertLessonAccess(user(username), lesson(id));
    }

    @Transactional(readOnly = true)
    public void requireCourse(String id, String username) {
        LearningPolicy.assertCourseAccess(user(username), course(id));
    }
}
