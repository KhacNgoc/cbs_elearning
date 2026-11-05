package com.bidv.coretraining.learner.service.model;

/** A published course the user may open; editors read it without enrolling or unlocking lessons. */
public record LearnableCourse(String id, String name, boolean editor) {
}
