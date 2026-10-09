package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @Transactional
public class TrainingStructureService {
    public record DomainDto(String id,String code,String name,String description,String status) {}
    public record ProgramDto(String id,String domainId,String code,String name,String description,String status) {}
    public record ChapterDto(String id,String courseId,String code,String title,String description,Integer chapterOrder,String status,Long version,List<LessonDto> lessons,boolean locked,boolean completed) {}
    public record OutlineDto(CourseDto course,List<ChapterDto> chapters,boolean completed,String resumeLessonId) {}
    public record CourseRequest(String code,String name,String programId,String description,String iconName,Long version) {}
    public record ChapterRequest(String code,String title,String description,Long version) {}
    private final TrainingDomainRepository domains;
    private final TrainingProgramRepository programs;
    private final CourseRepository courses;
    private final ChapterRepository chapters;
    private final LessonRepository lessons;
    private final UserRepository users;
    private final CourseMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired private LessonProgressRepository progress;
    public TrainingStructureService(TrainingDomainRepository domains,TrainingProgramRepository programs,
            CourseRepository courses,ChapterRepository chapters,LessonRepository lessons,UserRepository users,CourseMapper mapper) {
        this.domains=domains;this.programs=programs;this.courses=courses;this.chapters=chapters;this.lessons=lessons;this.users=users;this.mapper=mapper;
    }
    private String required(String value,String label) {
        if(value==null || value.isBlank()) throw new BadRequestException(label+" không được để trống");
        return value.trim();
    }
    private void draft(Course course) {
        if(!"DRAFT".equals(course.getStatus())) throw new BadRequestException("Chuyển khóa về bản nháp trước khi thay đổi cấu trúc");
    }
    private void version(Long actual,Long expected) {
        if(expected!=null && !Objects.equals(actual,expected))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Dữ liệu đã thay đổi. Hãy tải lại trang");
    }
    public Course course(String id) {return courses.findById(id).orElseThrow(()->new ResourceNotFoundException("Course","id",id));}
    public Chapter chapter(String id) {return chapters.findById(id).orElseThrow(()->new ResourceNotFoundException("Chapter","id",id));}
    public Lesson lesson(String id) {return lessons.findById(id).orElseThrow(()->new ResourceNotFoundException("Lesson","id",id));}
    public User user(String username) {return users.findByUsername(username).orElseThrow(()->new ResourceNotFoundException("User","username",username));}
    public Map<String,Object> catalog() {
        return Map.of("domains",domains.findAll().stream().map(d->new DomainDto(d.getId(),d.getCode(),d.getName(),d.getDescription(),d.getStatus())).toList(),
                "programs",programs.findAll().stream().map(p->new ProgramDto(p.getId(),p.getDomain().getId(),p.getCode(),p.getName(),p.getDescription(),p.getStatus())).toList());
    }
    public DomainDto saveDomain(String id,DomainDto request) {
        TrainingDomain d=id==null?TrainingDomain.builder().build():domains.findById(id).orElseThrow(()->new ResourceNotFoundException("Domain","id",id));
        d.setCode(required(request.code(),"Mã lĩnh vực")); d.setName(required(request.name(),"Tên lĩnh vực"));d.setDescription(request.description());
        d.setStatus("ARCHIVED".equals(request.status())?"ARCHIVED":"ACTIVE"); domains.save(d);
        return new DomainDto(d.getId(),d.getCode(),d.getName(),d.getDescription(),d.getStatus());
    }
    public ProgramDto saveProgram(String id,ProgramDto request) {
        TrainingProgram p=id==null?TrainingProgram.builder().build():programs.findById(id).orElseThrow(()->new ResourceNotFoundException("Program","id",id));
        p.setDomain(domains.findById(required(request.domainId(),"Lĩnh vực")).orElseThrow(()->new BadRequestException("Lĩnh vực không tồn tại")));
        p.setCode(required(request.code(),"Mã chương trình"));p.setName(required(request.name(),"Tên chương trình"));p.setDescription(request.description());
        p.setStatus("ARCHIVED".equals(request.status())?"ARCHIVED":"ACTIVE"); programs.save(p);
        return new ProgramDto(p.getId(),p.getDomain().getId(),p.getCode(),p.getName(),p.getDescription(),p.getStatus());
    }
    public CourseDto saveCourse(String id,CourseRequest r) {
        Course c=id==null?Course.builder().id(UUID.randomUUID().toString()).build():course(id);version(c.getVersion(),r.version());
        TrainingProgram p=programs.findById(required(r.programId(),"Chương trình")).orElseThrow(()->new BadRequestException("Chương trình không tồn tại"));
        if(!"ACTIVE".equals(p.getStatus()) || !"ACTIVE".equals(p.getDomain().getStatus())) throw new BadRequestException("Danh mục đã lưu trữ");
        c.setProgram(p);c.setCode(required(r.code(),"Mã khóa"));c.setName(required(r.name(),"Tên khóa"));c.setDescription(r.description());c.setIconName(r.iconName()==null?"BookOpen":r.iconName());
        return mapper.toCourseDto(courses.saveAndFlush(c));
    }
    public List<CourseDto> adminCourses() {return courses.findAll().stream().map(mapper::toCourseDto).toList();}
    public List<ChapterDto> adminChapters(String courseId) {course(courseId);return chapters.findByCourseIdOrderByChapterOrderAscIdAsc(courseId).stream().map(c->chapterDto(c,null)).toList();}
    public ChapterDto adminChapter(String id) {return chapterDto(chapter(id),null);}
    private ChapterDto chapterDto(Chapter c,User user) {
        boolean admin=user==null || "ROLE_ADMIN".equals(user.getRole());
        List<Lesson> list=admin?c.getLessons():LearningPolicy.publishedLessons(c);
        List<LessonDto> lessonDtos=mapper.toLessonDtoList(list);
        if(!admin)lessonDtos.forEach(TrainingStructureService::outlineOnly);
        return new ChapterDto(c.getId(),c.getCourse().getId(),c.getCode(),c.getTitle(),c.getDescription(),c.getChapterOrder(),c.getStatus(),c.getVersion(),lessonDtos,
                !admin && !LearningPolicy.chapterUnlocked(user,c),user!=null && LearningPolicy.chapterCompleted(user,c));
    }
    public static void outlineOnly(LessonDto dto) {
        dto.setVideoUrl(null);dto.setTextBody(null);dto.setDocuments(List.of());
        dto.setProcedureSteps(List.of());dto.setComments(List.of());
    }
    public ChapterDto saveChapter(String courseId,String id,ChapterRequest r) {
        Course course=course(courseId);draft(course); Chapter c=id==null?Chapter.builder().course(course).chapterOrder(course.getChapters().stream().mapToInt(Chapter::getChapterOrder).max().orElse(-1)+1).build():chapter(id);
        if(!c.getCourse().getId().equals(courseId)) throw new BadRequestException("Chương không thuộc khóa");version(c.getVersion(),r.version());
        c.setCode(required(r.code(),"Mã chương"));c.setTitle(required(r.title(),"Tên chương"));c.setDescription(r.description());
        return chapterDto(chapters.saveAndFlush(c),null);
    }
    private void noHistory(List<Lesson> list) {
        Set<String> ids=new HashSet<>(list.stream().map(Lesson::getId).toList());
        if(!ids.isEmpty() && progress.existsByLessonIdIn(ids))throw new BadRequestException("Nội dung đã có tiến độ học. Hãy lưu trữ khóa thay vì xóa");
        if(users.findAll().stream().anyMatch(u->u.getCompletedLessons().stream().anyMatch(l->ids.contains(l.getId())) || u.getQuizScores().stream().anyMatch(q->q.getLesson()!=null && ids.contains(q.getLesson().getId()))))
            throw new BadRequestException("Nội dung đã có lịch sử học. Hãy lưu trữ khóa thay vì xóa");
    }
    public void deleteChapter(String id) {Chapter c=chapter(id);draft(c.getCourse());noHistory(c.getLessons());chapters.delete(c);chapters.flush();}
    public void deleteLesson(String id) {Lesson l=lesson(id);draft(l.getCourse());noHistory(List.of(l));lessons.delete(l);lessons.flush();}
    public void deleteCourse(String id) {Course c=course(id);draft(c);noHistory(c.getLessons());if(!users.findAll().stream().noneMatch(u->u.getRegisteredCourses().contains(c)))throw new BadRequestException("Khóa đã có học viên đăng ký");courses.delete(c);}
    public void reorderChapters(String id,List<String> ids) {
        Course course=course(id);draft(course);List<Chapter> current=chapters.findByCourseIdOrderByChapterOrderAscIdAsc(id);
        validateOrder(ids,current.stream().map(Chapter::getId).toList());for(int i=0;i<current.size();i++)current.get(i).setChapterOrder(-i-1);chapters.flush();for(int i=0;i<ids.size();i++){Chapter c=chapter(ids.get(i));c.setChapterOrder(i);}chapters.flush();
    }
    public void reorderLessons(String id,List<String> ids) {
        Chapter chapter=chapter(id);draft(chapter.getCourse());List<Lesson> current=lessons.findByChapterIdOrderByLessonOrderAscIdAsc(id);
        validateOrder(ids,current.stream().map(Lesson::getId).toList());for(int i=0;i<current.size();i++)current.get(i).setLessonOrder(-i-1);lessons.flush();for(int i=0;i<ids.size();i++)lesson(ids.get(i)).setLessonOrder(i);lessons.flush();
    }
    private void validateOrder(List<String> ids,List<String> current) {if(ids==null || ids.size()!=current.size() || new HashSet<>(ids).size()!=ids.size() || !new HashSet<>(ids).equals(new HashSet<>(current)))throw new BadRequestException("Danh sách thứ tự phải đầy đủ, không trùng và cùng cấp");}
    public CourseDto status(String id,String status) {
        Course c=course(id);if(!Set.of("DRAFT","PUBLISHED","ARCHIVED").contains(status))throw new BadRequestException("Trạng thái không hợp lệ");
        if("PUBLISHED".equals(status)) {
            if(c.getChapters().isEmpty())throw new BadRequestException("Khóa chưa có chương");
            for(Chapter ch:c.getChapters()) {
                if(ch.getLessons().isEmpty())throw new BadRequestException("Chương "+ch.getTitle()+" chưa có bài");
                for(Lesson l:ch.getLessons()) {
                    if(l.getContent()==null || (blank(l.getVideoUrl()) && blank(l.getTextBody()) && l.getDocuments().isEmpty() && l.getProcedureSteps().isEmpty()))throw new BadRequestException("Bài "+l.getTitle()+" chưa có nội dung");
                    if(l.getQuiz()!=null && l.getQuiz().getQuestions().isEmpty())throw new BadRequestException("Bài "+l.getTitle()+" có kiểm tra chưa có câu hỏi");
                    l.setStatus("PUBLISHED");
                } ch.setStatus("PUBLISHED");
            }
        } c.setStatus(status);return mapper.toCourseDto(courses.save(c));
    }
    private boolean blank(String s){return s==null||s.isBlank();}
    public OutlineDto outline(String id,String username) {
        Course course=course(id);User user=user(username);LearningPolicy.assertCourseAccess(user,course);
        List<Chapter> list="ROLE_ADMIN".equals(user.getRole())?course.getChapters():LearningPolicy.publishedChapters(course);
        List<ChapterDto> dto=list.stream().map(ch->chapterDto(ch,user)).toList();
        String resume=list.stream().flatMap(ch->LearningPolicy.publishedLessons(ch).stream()).filter(l->!LearningPolicy.completed(user,l) && LearningPolicy.lessonUnlocked(user,l)).map(Lesson::getId).findFirst().orElse(null);
        CourseDto courseDto=mapper.toCourseDto(course);
        if(!"ROLE_ADMIN".equals(user.getRole()))courseDto.getLessons().forEach(TrainingStructureService::outlineOnly);
        return new OutlineDto(courseDto,dto,!dto.isEmpty()&&dto.stream().allMatch(ChapterDto::completed),resume);
    }
    public void requireLesson(String id,String username) {LearningPolicy.assertLessonAccess(user(username),lesson(id));}
}
