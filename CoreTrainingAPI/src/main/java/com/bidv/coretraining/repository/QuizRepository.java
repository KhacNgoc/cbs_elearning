package com.bidv.coretraining.repository;
import com.bidv.coretraining.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
public interface QuizRepository extends JpaRepository<Quiz,String> { java.util.Optional<Quiz> findByLessonId(String lessonId); }
