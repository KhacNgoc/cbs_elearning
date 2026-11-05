-- Lesson flashcards and per-learner spaced-repetition state.
-- Hibernate ddl-auto=update creates these tables on startup; this idempotent script
-- is for environments whose schema is managed manually.
CREATE TABLE IF NOT EXISTS flashcards (
    id bigserial PRIMARY KEY,
    version bigint,
    course_id varchar(255) NOT NULL REFERENCES courses (id),
    lesson_id varchar(255) NOT NULL REFERENCES lessons (id),
    type varchar(20) NOT NULL,
    front varchar(1000) NOT NULL,
    back text NOT NULL,
    origin varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    source_document_id varchar(255),
    source_document_name varchar(500),
    source_page integer,
    source_section varchar(2000),
    card_order integer NOT NULL DEFAULT 0,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_flashcards_lesson_order ON flashcards (lesson_id, card_order);
CREATE INDEX IF NOT EXISTS idx_flashcards_course_status ON flashcards (course_id, status);

CREATE TABLE IF NOT EXISTS flashcard_reviews (
    id bigserial PRIMARY KEY,
    version bigint,
    user_id bigint NOT NULL REFERENCES users (id),
    flashcard_id bigint NOT NULL REFERENCES flashcards (id),
    due_at timestamp(6) NOT NULL,
    interval_days integer NOT NULL,
    ease_factor double precision NOT NULL,
    repetitions integer NOT NULL,
    lapses integer NOT NULL,
    last_rating varchar(20) NOT NULL,
    last_reviewed_at timestamp(6) NOT NULL,
    CONSTRAINT uk_flashcard_reviews_user_card UNIQUE (user_id, flashcard_id)
);
CREATE INDEX IF NOT EXISTS idx_flashcard_reviews_user_due ON flashcard_reviews (user_id, due_at);
