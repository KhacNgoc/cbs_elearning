import { axiosClient } from './axiosClient';
import type { LearnerAttempt, LearnerCourse, LeaderboardUser, Badge } from '../types';
export interface AssessmentGroup { latest: LearnerAttempt; best: LearnerAttempt; attemptCount: number; courseName: string }
export interface ResultPage { items: AssessmentGroup[]; total: number; bestScore: number | null; legacyTotal: number; legacyScores: {courseId:string;lessonId:string;title:string;score:number;total:number;takenAt:string;courseName:string}[] }
export interface ProfileStats { joined:number; completed:number; total:number; percent:number|null; notStarted:number; inProgress:number; complete:number; mandatory:number; mandatoryComplete:number; competencies:{id:string;label:string;count:number;value:number|null}[] }
export interface AwardsPage { points:number;total:number;completedLessons:number;totalLessons:number;items:Badge[] }
export const profileReadApi = {
 async awards(page=0) { return (await axiosClient.get<AwardsPage>('/api/users/me/awards',{params:{page,size:20}})).data; },
 async results(page=0,size=10,search='') { return (await axiosClient.get<ResultPage>('/api/users/me/assessments',{params:{page,size,search}})).data; },
 async history(quizId:string,revision:number,cursor?:string) { return (await axiosClient.get<{items:LearnerAttempt[];nextCursor:string|null;hasMore:boolean}>('/api/users/me/assessments/history',{params:{quizId,revision,cursor,size:20}})).data; },
 async stats() { return (await axiosClient.get<ProfileStats>('/api/users/me/assessment-statistics')).data; },
 async courses(page=0) { return (await axiosClient.get<{items:LearnerCourse[];total:number}>('/api/users/me/learning/dashboard/courses',{params:{page:page+1,size:10,tab:'mine',sort:'name'}})).data; },
 async leaderboard(page=0,size=25) { return (await axiosClient.get<{items:LeaderboardUser[];total:number;currentUser:LeaderboardUser|null}>('/api/leaderboard/page',{params:{page,size}})).data; },
};
