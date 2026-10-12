export interface Comment {
  id: number;
  user: string;
  avatar: string;
  rating?: number;
  content: string;
  date: string;
  parentId?: number;
  replyToUser?: string;
  replies?: Comment[];
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
  id?: number;
  name: string;
  documentType: 'PDF' | 'WORD' | 'POWERPOINT';
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
  videoSource?: 'UPLOAD' | 'URL';
  hasQuiz?: boolean;
  quizPassingScore?: number;
  procedureSteps: ProcedureSection[];
  comments: Comment[];
}

export interface Course {
  allowedActions?: string[];
  adminActions?: Record<string,string[]>;
  canEdit?: boolean;
  id: string;
  name: string;
  code?: string;
  programId?: string;
  programName?: string;
  domainName?: string;
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
  allowedActions?:string[]; id: string; code: string; name: string; description?: string; status: string }
export interface TrainingProgram extends TrainingDomain { domainId: string; unitIds?:string[]; allowedActions?:string[] }
export interface Chapter { id: string; courseId: string; code: string; title: string; description?: string; chapterOrder: number; status: string; version?: number; lessons: Lesson[]; locked: boolean; completed: boolean }
export interface CourseOutline { course: Course; chapters: Chapter[]; completed: boolean; resumeLessonId?: string }

export interface Question {
  id: number;
  courseId: string;
  lessonId?: string;
  text: string;
  options: string[];
  correctAnswer: number; // 0-indexed index of options
  correctAnswers?: number[];
  type?: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE';
}

export type QuizQuestion = Omit<Question, 'correctAnswer' | 'correctAnswers'>;

export interface Badge {
  id: string;
  title: string;
  type: 'bronze' | 'silver' | 'gold';
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
  groupId?: string;
  groupName?: string;
  mandatoryCourses: string[];
  registeredCourses: string[];
  mustChangePassword?: boolean;
  pendingCourses: string[];
}

export interface LessonProgress {
  lessonId: string;
  lastPositionSeconds: number;
  watchedSeconds: number;
  durationSeconds: number;
}

export type LessonProgressRequest = Omit<LessonProgress, 'lessonId'>;

export interface UserProfile {
  roleNames?: Record<string,string>;
  systemAdmin?:boolean;
  permissionRevision?:number;
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
  scopeUnitIds?: string[];
  status?: string;
  learnerType?: string;
  points: number;
  groupId?: string;
  groupName?: string;
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
