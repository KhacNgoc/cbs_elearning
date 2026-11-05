-- Flashcards no longer keep a review schedule. Hibernate ddl-auto=update never drops a table, and this one's
-- foreign key to flashcards would block deleting any reviewed card, so every environment must run this script.
-- Permanently deletes learners' review history.
DROP TABLE IF EXISTS flashcard_reviews;
