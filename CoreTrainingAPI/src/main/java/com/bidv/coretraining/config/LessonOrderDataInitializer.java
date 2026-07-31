package com.bidv.coretraining.config;

import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.TopicRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class LessonOrderDataInitializer implements ApplicationRunner {

    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;

    public LessonOrderDataInitializer(
            TopicRepository topicRepository,
            LessonRepository lessonRepository) {
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        topicRepository.findAll().forEach(topic -> initializeTopicOrder(topic.getId()));
    }

    private void initializeTopicOrder(String topicId) {
        List<Lesson> lessons =
                lessonRepository.findByTopicIdOrderByLessonOrderAscIdAsc(topicId);
        Set<Integer> positions = new HashSet<>();
        boolean needsInitialization = lessons.stream()
                .map(Lesson::getLessonOrder)
                .anyMatch(order -> order == null || !positions.add(order));
        if (!needsInitialization) return;

        for (int index = 0; index < lessons.size(); index++) {
            lessons.get(index).setLessonOrder(index);
        }
        lessonRepository.saveAll(lessons);
    }
}
