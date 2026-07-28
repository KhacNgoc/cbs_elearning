export interface Comment {
  id: string;
  user: string;
  avatar: string;
  rating: number;
  content: string;
  date: string;
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
  hint: string;
  documents: LessonDocument[];
  videoUrl: string;
  videoSource?: 'UPLOAD' | 'URL';
  hasQuiz?: boolean;
  procedureSteps: ProcedureSection[];
  comments: Comment[];
}

export interface Topic {
  id: string;
  name: string;
  iconName: string;
  lessons: Lesson[];
}

export interface Question {
  id: number;
  topicId: string;
  lessonId?: string;
  text: string;
  options: string[];
  correctAnswer: number; // 0-indexed index of options
}

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
  points: number; // Experience points
  unlockedBadges: Badge[];
  groupId?: string;
  groupName?: string;
  mandatoryTopics: string[];
  registeredTopics: string[];
  mustChangePassword?: boolean;
}

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
