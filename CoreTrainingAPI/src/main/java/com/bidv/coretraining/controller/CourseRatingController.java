package com.bidv.coretraining.controller;
import com.bidv.coretraining.dto.CourseRatingDto;
import com.bidv.coretraining.service.CourseRatingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
public class CourseRatingController {
    private final CourseRatingService ratings;
    public CourseRatingController(CourseRatingService ratings){this.ratings=ratings;}
    @GetMapping("/api/courses/{id}/rating") public CourseRatingDto.State state(@PathVariable String id){return ratings.state(id);}
    @PutMapping("/api/courses/{id}/rating") public CourseRatingDto.State save(@PathVariable String id,@Valid @RequestBody CourseRatingDto.Request request){return ratings.save(id,request);}
    @GetMapping("/api/admin/courses/ratings") public CourseRatingDto.Report report(@RequestParam(required=false) String courseId,@RequestParam(defaultValue="false") boolean lowOnly,@RequestParam(defaultValue="0") @jakarta.validation.constraints.Min(0) int page){return ratings.report(courseId,lowOnly,page);}
}
