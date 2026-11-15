export interface Comment {
  id: number;
  user: string;
  avatar: string;
  rating?: number;
  content: string;
  date: string;
  createdAt?: string;
  parentId?: number;
  replyToUser?: string;
  replies?: Comment[];
  repliesHasMore?: boolean;
  replyCursor?: string | null;
}

export interface ReplyPage {
  content: Comment[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface ReplyPageState {
  loading: boolean;
  error: string;
}

export interface VideoNote {
  id: number;
  lessonId: string;
  timestampSeconds: number;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProcedureSection {
  title: string;
  steps: string[];
}

export interface LessonDocument {
  purpose?: 'SUPPORTING' | 'SLIDE';
  id?: number;
  name: string;
  documentType: "PDF" | "WORD" | "POWERPOINT";
  fileUrl: string;
  originalFileName?: string;
  fileSize?: number;
  documentOrder?: number;
}

export interface Lesson {
  id: string;
  title: string;
  courseId: string;
  chapterId?: string;
  status?: string;
  version?: number;
  textBody?: string;
  quizRequired?: boolean;
  lessonOrder?: number;
  hint: string;
  documents: LessonDocument[];
  videoUrl: string;
  videoSource?: "UPLOAD" | "URL";
  hasQuiz?: boolean;
  hasVideo?: boolean;
  hasText?: boolean;
  /** Outline flag for editors: the lesson has procedure steps. */
  hasProcedure?: boolean;
  /** Outline counts: lists show badges without loading documents or procedure steps. */
  documentCount?: number;
  procedureStepCount?: number;
  textRequired?: boolean;
  videoRequired?: boolean;
  slideRequired?: boolean;
  quizPassingScore?: number;
  procedureSteps: ProcedureSection[];
  comments: Comment[];
  commentCount?: number;
}

export interface Course {
  allowedActions?: string[];
  adminActions?: Record<string, string[]>;
  canEdit?: boolean;
  id: string;
  name: string;
  code?: string;
  programId?: string;
  programName?: string;
  domainName?: string;
  domainId?: string;
  thumbnail?: string | null;
  businessModule?: string;
  seqNumber?: number;
  unitIds?: string[];
  mandatoryUnitIds?: string[];
  optionalUnitIds?: string[];
  level?: string;
  keywords?: string;
  completionDeadline?: string | null;
  description?: string;
  status?: string;
  version?: number;
  chapterCount?: number;
  iconName: string;
  hasQuiz?: boolean;
  quizPassingScore?: number;
  lessons: Lesson[];
}

export interface TrainingDomain {
  allowedActions?: string[];
  id: string;
  code: string;
  name: string;
  description?: string;
  status: string;
  businessModule?: string;
  seqNumber?: number;
  unitIds?: string[];
  mandatoryUnitIds?: string[];
  optionalUnitIds?: string[];
}

export interface TrainingProgram extends TrainingDomain {
  domainId: string;
  level?: string;
  autoEnrollStaff?: boolean;
}

export interface Chapter {
  id: string;
  courseId: string;
  code: string;
  title: string;
  description?: string;
  chapterOrder: number;
  status: string;
  version?: number;
  lessons: Lesson[];
  locked: boolean;
  completed: boolean;
}

export interface CourseOutline {
  course: Course;
  chapters: Chapter[];
  completed: boolean;
  resumeLessonId?: string;
  resume?: ResumeTarget;
}

export interface Question {
  id: number;
  courseId: string;
  lessonId?: string;
  text: string;
  explanation?: string;
  bankQuestionId?: number;
  bankQuestionCode?: string;
  options: string[];
  correctAnswer: number; // 0-indexed index of options
  correctAnswers?: number[];
  type?: "SINGLE_CHOICE" | "MULTIPLE_CHOICE";
}

export type QuizQuestion = Omit<Question, "correctAnswer" | "correctAnswers">;

export interface Badge {
  id: string;
  title: string;
  type: "bronze" | "silver" | "gold";
  courseId: string;
  courseName: string;
  unlockedAt: string;
  icon: string;
}

export interface UserProgress {
  completedLessons: string[]; // List of completed lesson IDs
  quizScores: Record<string, number>; // courseId -> max score achieved (e.g. 3)
  quizTotals: Record<string, number>; // courseId -> total questions (e.g. 5)
  lessonQuizScores: Record<string, number>; // lessonId -> max score achieved
  lessonQuizTotals: Record<string, number>; // lessonId -> total questions
  lessonProgress?: Record<string, LessonProgress>;
  points: number; // Experience points
  unlockedBadges: Badge[];
  mandatoryCourses: string[];
  registeredCourses: string[];
  mustChangePassword?: boolean;
  pendingCourses: string[];
}

export interface LessonProgress {
  textCompleted?: boolean;
  documentCompleted?: boolean;
  lessonCompleted?: boolean;
  lessonId: string;
  lastPositionSeconds: number;
  watchedSeconds: number;
  durationSeconds: number;
  updatedAt?: string;
  contentType?: "VIDEO" | "DOCUMENT" | "TEXT";
  documentId?: number;
  documentPage?: number;
  documentPositions?: Record<string, { page: number; scrollOffset: number; completed?: boolean }>;
  scrollOffset?: number;
  contentCompleted?: boolean;
}

export type LessonProgressRequest = Partial<
  Omit<LessonProgress, "lessonId" | "updatedAt" | "contentCompleted" | "documentPositions">
>;

export interface ResumeTarget {
  courseId: string;
  lessonId: string;
  lessonTitle: string;
  courseName: string;
  contentType: "VIDEO" | "DOCUMENT" | "TEXT" | "QUIZ";
  documentId?: number;
  documentPage?: number;
  scrollOffset?: number;
  positionSeconds?: number;
  lastAccessedAt?: string;
}

export interface LearnerCourse {
  course: Course;
  source: "ASSIGNED" | "REGISTERED" | "AVAILABLE" | "PENDING";
  mandatory: boolean;
  learningStatus:
    | "NOT_STARTED"
    | "IN_PROGRESS"
    | "COMPLETED"
    | "AVAILABLE"
    | "PENDING";
  completedCount: number;
  totalCount: number;
  percent: number;
  dueDate?: string;
  lastAccessedAt?: string;
  resume?: ResumeTarget;
}

export interface LearnerAttempt {
  id: string;
  quizId: string;
  courseId: string;
  lessonId: string;
  title: string;
  domainId: string;
  domainName: string;
  revision: number;
  score: number;
  total: number;
  passingScore: number;
  passed: boolean;
  takenAt: string;
}

export interface UserProfile {
  roleNames?: Record<string, string>;
  systemAdmin?: boolean;
  permissionRevision?: number;
  id?: number;
  username: string;
  fullName: string;
  email?: string;
  employeeId?: string;
  avatarColor?: string;
  role: string;
  roles?: string[];
  permissions?: string[];
  unitId?: string;
  unitName?: string;
  scopeUnitIds?: string[];
  status?: string;
  learnerType?: string;
  points: number;
}

export interface LeaderboardUser {
  rank: number;
  username: string;
  fullName: string;
  points: number;
  avatarColor: string;
  badgesCount: number;
  completedLessonsCount: number;
}
