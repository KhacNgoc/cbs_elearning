package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.exception.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.*;

@Service @Transactional(readOnly=true)
public class CourseRatingService {
    public record Request(@NotNull @Min(1) @Max(5) Integer stars,@Size(max=2000) String content,Long version) {}
    public record Rating(Long id,Long version,int stars,String content,LocalDateTime updatedAt) {}
    public record Summary(String courseId,String courseName,long count,Double average,long completed,long low,Double responseRate) {}
    public record State(boolean canRate,String message,Rating mine,Summary summary) {}
    public record Feedback(Long id,String courseId,String courseName,String learner,int stars,String content,LocalDateTime updatedAt) {}
    public record Report(List<Summary> courses,long count,Double average,long completed,long low,Double responseRate,Map<Integer,Long> distribution,List<Feedback> feedback,long totalFeedback,int page,int totalPages) {}
    private final CourseRatingRepository ratings;
    private final CourseRepository courses;
    private final UserRepository users;
    private final AuthorizationService auth;
    public CourseRatingService(CourseRatingRepository ratings,CourseRepository courses,UserRepository users,AuthorizationService auth){this.ratings=ratings;this.courses=courses;this.users=users;this.auth=auth;}
    public static boolean eligible(User user,Course course) {
        boolean enrolled=user.getRegisteredCourses().stream().anyMatch(c->c.getId().equals(course.getId()))
                || AccessPolicy.mandatory(user,course) || user.hasGroupMandatoryCourse(course.getId());
        return AccessPolicy.has(user,"LEARN") && "PUBLISHED".equals(course.getStatus()) && enrolled
                && LearningPolicy.courseCompleted(user,course);
    }
    private Course course(String id){return courses.findById(id).orElseThrow(()->new ResourceNotFoundException("Course"));}
    private Rating dto(CourseRating r){return r==null?null:new Rating(r.getId(),r.getVersion(),r.getStars(),r.getContent(),r.getUpdatedAt());}
    private Summary summary(Course course){
        var list=ratings.aggregate(List.of(course.getId()));var a=list.isEmpty()?null:list.get(0);
        long count=a==null?0:a.getCount(),completed=ratings.completedCount(course.getId());
        return new Summary(course.getId(),course.getName(),count,a==null?null:a.getAverage(),completed,a==null?0:a.getLow(),completed==0?null:count*100.0/completed);
    }
    public State state(String courseId){
        var user=auth.current();var course=course(courseId);LearningPolicy.assertCourseAccess(user,course);
        boolean allowed=eligible(user,course);
        return new State(allowed,allowed?"Đánh giá trải nghiệm của bạn sau khi hoàn thành khóa học.":"Hoàn thành khóa học để gửi đánh giá.",dto(ratings.findByUserIdAndCourseId(user.getId(),courseId).orElse(null)),summary(course));
    }
    @Transactional public State save(String courseId,Request request){
        var user=auth.current();var course=ratings.lockCourse(courseId).orElseThrow(()->new ResourceNotFoundException("Course"));
        LearningPolicy.assertCourseAccess(user,course);
        if(!eligible(user,course))throw new BadRequestException("Bạn cần hoàn thành khóa học và các kiểm tra bắt buộc trước khi đánh giá.");
        if(request.stars()==null||request.stars()<1||request.stars()>5||request.content()!=null&&request.content().length()>2000)throw new BadRequestException("Đánh giá không hợp lệ.");
        var existing=ratings.findByUserIdAndCourseId(user.getId(),courseId).orElse(null);
        if(existing!=null&&!Objects.equals(existing.getVersion(),request.version()))throw new BadRequestException("Đánh giá đã thay đổi. Hãy tải lại trước khi cập nhật.");
        if(existing==null&&request.version()!=null)throw new BadRequestException("Không tìm thấy đánh giá cần cập nhật.");
        var now=LocalDateTime.now();var rating=existing==null?CourseRating.builder().user(user).course(course).createdAt(now).build():existing;
        rating.setStars(request.stars());rating.setContent(request.content()==null?"":request.content().strip());rating.setUpdatedAt(now);
        LearningPolicy.recordCourseCompletion(user,course);users.save(user);ratings.saveAndFlush(rating);
        return new State(true,"Đã lưu đánh giá khóa học.",dto(rating),summary(course));
    }
    public Report report(String courseId,boolean lowOnly,int page){
        auth.requireOperation("courses","read");
        var allowed=auth.editableCourses().stream().filter(c->courseId==null||courseId.isBlank()||c.getId().equals(courseId)).toList();
        if(courseId!=null&&!courseId.isBlank()&&allowed.isEmpty())throw new org.springframework.security.access.AccessDeniedException("Không có quyền xem đánh giá khóa học này.");
        var summaries=allowed.stream().map(this::summary).toList();
        long count=summaries.stream().mapToLong(Summary::count).sum(),completed=summaries.stream().mapToLong(Summary::completed).sum(),low=summaries.stream().mapToLong(Summary::low).sum();
        Double average=count==0?null:summaries.stream().filter(s->s.average()!=null).mapToDouble(s->s.average()*s.count()).sum()/count;
        var distribution=new TreeMap<Integer,Long>();for(int i=1;i<=5;i++)distribution.put(i,0L);
        if(allowed.isEmpty())return new Report(summaries,0,null,0,0,null,distribution,List.of(),0,0,0);
        var ids=allowed.stream().map(Course::getId).toList();
        ratings.distribution(ids).forEach(s->distribution.put(s.getStars(),s.getCount()));
        var feedback=ratings.feedback(ids,lowOnly,PageRequest.of(Math.max(0,page),20,Sort.by(Sort.Order.desc("updatedAt"),Sort.Order.desc("id"))));
        return new Report(summaries,count,average,completed,low,completed==0?null:count*100.0/completed,distribution,feedback.stream().map(r->new Feedback(r.getId(),r.getCourse().getId(),r.getCourse().getName(),r.getUser().getFullName(),r.getStars(),r.getContent(),r.getUpdatedAt())).toList(),feedback.getTotalElements(),feedback.getNumber(),feedback.getTotalPages());
    }
}
