package com.bidv.coretraining.course.service.impl;
import com.bidv.coretraining.policy.LearningPolicy;


import com.bidv.coretraining.course.dto.CourseRatingDto.*;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.entity.CourseRating;
import com.bidv.coretraining.course.repository.CourseRatingReportRepository;
import com.bidv.coretraining.course.repository.CourseRatingRepository;
import com.bidv.coretraining.report.service.ReportScopeResolver;
import com.bidv.coretraining.security.ReportPages;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.course.service.CourseRatingService;
import com.bidv.coretraining.exception.*;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.user.repository.UserRepository;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.*;

@Service @Transactional(readOnly=true)
public class CourseRatingServiceImpl implements CourseRatingService {
    static final int FEEDBACK_PAGE_SIZE = 10;


    private final CourseRatingRepository ratings;
    private final CourseRepository courses;
    private final UserRepository users;
    private final AuthorizationService auth;
    private final CourseRatingReportRepository reports;
    private final ReportScopeResolver scopes;
    public CourseRatingServiceImpl(CourseRatingRepository ratings,CourseRepository courses,UserRepository users,AuthorizationService auth,CourseRatingReportRepository reports,ReportScopeResolver scopes){this.ratings=ratings;this.courses=courses;this.users=users;this.auth=auth;this.reports=reports;this.scopes=scopes;}
    private Course course(String id){return courses.findById(id).orElseThrow(()->new ResourceNotFoundException("Course"));}
    private Rating dto(CourseRating r){return r==null?null:new Rating(r.getId(),r.getVersion(),r.getStars(),r.getContent(),r.getUpdatedAt());}
    private Summary summary(Course course){
        var list=ratings.aggregate(List.of(course.getId()));var a=list.isEmpty()?null:list.get(0);
        long count=a==null?0:a.getCount(),completed=ratings.completedCount(course.getId());
        return new Summary(course.getId(),course.getName(),count,a==null?null:a.getAverage(),completed,a==null?0:a.getLow(),completed==0?null:count*100.0/completed);
    }
    public State state(String courseId){
        var user=auth.current();var course=course(courseId);LearningPolicy.assertCourseAccess(user,course);
        boolean allowed=LearningPolicy.ratingEligible(user,course);
        return new State(allowed,allowed?"Đánh giá trải nghiệm của bạn sau khi hoàn thành khóa học.":"Hoàn thành khóa học để gửi đánh giá.",dto(ratings.findByUserIdAndCourseId(user.getId(),courseId).orElse(null)),summary(course));
    }
    @Transactional public State save(String courseId,Request request){
        var user=auth.current();var course=ratings.lockCourse(courseId).orElseThrow(()->new ResourceNotFoundException("Course"));
        LearningPolicy.assertCourseAccess(user,course);
        if(!LearningPolicy.ratingEligible(user,course))throw new BadRequestException("Bạn cần hoàn thành khóa học và các kiểm tra bắt buộc trước khi đánh giá.");
        if(request.stars()==null||request.stars()<1||request.stars()>5||request.content()!=null&&request.content().length()>2000)throw new BadRequestException("Đánh giá không hợp lệ.");
        var existing=ratings.findByUserIdAndCourseId(user.getId(),courseId).orElse(null);
        if(existing!=null&&!Objects.equals(existing.getVersion(),request.version()))throw new BadRequestException("Đánh giá đã thay đổi. Hãy tải lại trước khi cập nhật.");
        if(existing==null&&request.version()!=null)throw new BadRequestException("Không tìm thấy đánh giá cần cập nhật.");
        var now=LocalDateTime.now();var rating=existing==null?CourseRating.builder().user(user).course(course).createdAt(now).build():existing;
        rating.setStars(request.stars());rating.setContent(request.content()==null?"":request.content().strip());rating.setUpdatedAt(now);
        LearningPolicy.recordCourseCompletion(user,course);users.save(user);ratings.saveAndFlush(rating);
        return new State(true,"Đã lưu đánh giá khóa học.",dto(rating),summary(course));
    }
    /**
     * The rating report of the dashboard and the course report. It is a report, not course administration: it is
     * read with a report page (overview or the course report) and counts only the ratings and completions of
     * learners in the units of the roles granting that page.
     */
    public Report report(String courseId,boolean lowOnly,int page){
        auth.requireOperation("reports","read");
        var actor=auth.current();
        ReportScopeResolver.require(actor,ReportPages.RATINGS);
        var scope=scopes.resolve(actor,ReportPages.RATINGS);
        Map<Integer,Set<String>> grantUnits=new HashMap<>();
        for(int i=0;i<scope.roles().size();i++)grantUnits.put(i,scopes.grantUnits(scope.roles().get(i).unitIds()));
        var allowed=reports.courses(scope,grantUnits).stream().filter(c->courseId==null||courseId.isBlank()||c.id().equals(courseId)).toList();
        if(courseId!=null&&!courseId.isBlank()&&allowed.isEmpty())throw new org.springframework.security.access.AccessDeniedException("Không có quyền xem đánh giá khóa học này.");
        var ids=allowed.stream().map(CourseRatingReportRepository.CourseName::id).toList();
        var figures=reports.aggregate(scope,ids);var completions=reports.completed(scope,ids);
        var summaries=allowed.stream().map(c->{
            var f=figures.get(c.id());long count=f==null?0:f.count(),completed=completions.getOrDefault(c.id(),0L);
            return new Summary(c.id(),c.name(),count,f==null?null:f.average(),completed,f==null?0:f.low(),completed==0?null:count*100.0/completed);
        }).toList();
        long count=summaries.stream().mapToLong(Summary::count).sum(),completed=summaries.stream().mapToLong(Summary::completed).sum(),low=summaries.stream().mapToLong(Summary::low).sum();
        Double average=count==0?null:summaries.stream().filter(s->s.average()!=null).mapToDouble(s->s.average()*s.count()).sum()/count;
        var distribution=new TreeMap<Integer,Long>();for(int i=1;i<=5;i++)distribution.put(i,0L);
        if(allowed.isEmpty())return new Report(summaries,0,null,0,0,null,distribution,List.of(),0,0,0);
        reports.distribution(scope,ids).forEach(distribution::put);
        // Ten per page keeps the latest-feedback block about as tall as the rest of the report.
        int current=Math.max(0,page);long total=reports.feedbackCount(scope,ids,lowOnly);
        var feedback=reports.feedback(scope,ids,lowOnly,current,FEEDBACK_PAGE_SIZE);
        return new Report(summaries,count,average,completed,low,completed==0?null:count*100.0/completed,distribution,feedback,total,current,(int)((total+FEEDBACK_PAGE_SIZE-1)/FEEDBACK_PAGE_SIZE));
    }
}
