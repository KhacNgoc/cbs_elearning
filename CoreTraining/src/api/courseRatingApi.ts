import { axiosClient } from './axiosClient';
export interface SavedCourseRating {id:number;version:number;stars:number;content:string;updatedAt:string}
export interface RatingSummary {courseId:string;courseName:string;count:number;average:number|null;completed:number;low:number;responseRate:number|null}
export interface CourseRatingState {canRate:boolean;message:string;mine:SavedCourseRating|null;summary:RatingSummary}
export interface LiveRatingReport {courses:RatingSummary[];count:number;average:number|null;completed:number;low:number;responseRate:number|null;distribution:Record<string,number>;feedback:{id:number;courseId:string;courseName:string;learner:string;stars:number;content:string;updatedAt:string}[];totalFeedback:number;page:number;totalPages:number}
export const courseRatingApi={
  async state(courseId:string){return (await axiosClient.get<CourseRatingState>(`/api/courses/${encodeURIComponent(courseId)}/rating`)).data;},
  async save(courseId:string,request:{stars:number;content:string;version:number|null}){return (await axiosClient.put<CourseRatingState>(`/api/courses/${encodeURIComponent(courseId)}/rating`,request)).data;},
  async report(courseId='',lowOnly=false,page=0){return (await axiosClient.get<LiveRatingReport>('/api/admin/courses/ratings',{params:{courseId:courseId||undefined,lowOnly,page}})).data;},
};
