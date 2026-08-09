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
  topicId: string;
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

export interface Topic {
  id: string;
  name: string;
  iconName: string;
  hasQuiz?: boolean;
  quizPassingScore?: number;
  lessons: Lesson[];
}

export interface Question {
  id: number;
  topicId: string;
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
  topicId: string;
  topicName: string;
  unlockedAt: string;
  icon: string;
}

export interface UserProgress {
  completedLessons: string[]; // List of completed lesson IDs
  quizScores: Record<string, number>; // topicId -> max score achieved (e.g. 3)
  quizTotals: Record<string, number>; // topicId -> total questions (e.g. 5)
  lessonQuizScores: Record<string, number>; // lessonId -> max score achieved
  lessonQuizTotals: Record<string, number>; // lessonId -> total questions
  lessonProgress?: Record<string, LessonProgress>;
  points: number; // Experience points
  weeklyPoints?: number;
  unlockedBadges: Badge[];
  groupId?: string;
  groupName?: string;
  mandatoryTopics: string[];
  registeredTopics: string[];
  mustChangePassword?: boolean;
  pendingTopics: string[];
}

export interface LessonProgress {
  lessonId: string;
  lastPositionSeconds: number;
  watchedSeconds: number;
  durationSeconds: number;
}

export type LessonProgressRequest = Omit<LessonProgress, 'lessonId'>;

export interface UserProfile {
  id?: number;
  username: string;
  fullName: string;
  email?: string;
  employeeId?: string;
  avatarColor?: string;
  role: string;
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
