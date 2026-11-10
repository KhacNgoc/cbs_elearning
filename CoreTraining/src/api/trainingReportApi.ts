import { axiosClient } from './axiosClient';
import type { ReportingRecord } from '../utils/reportingMetrics';
export interface ReportRow extends ReportingRecord {
  id:string; name:string; unit:string; courseId:string; courseName:string;
  program:string; domain:string; completedAt:string|null; date:string;
  status:string; score:number|null; passed:boolean;
  attemptCount?:number;
  /** Passed every quiz taken, but a required quiz is still untaken. */
  requiredPending?:boolean;
}
export async function getTrainingReport():Promise<{asOf:string;rows:ReportRow[]}> {
  const {data}=await axiosClient.get('/api/admin/reports/training');
  return {asOf:data.asOf,rows:data.rows};
}
export interface ReportMetrics {
  name?:string; learnerName?:string; total:number;people:number;complete:number;learning:number;notStarted:number;
  evaluatedPeople?:number;tested:number;passed:number;failed:number;firstPassed:number;average:number|null;withDeadline:number;overdue:number;
  overduePeople:number;peopleWithDeadline:number;mandatory:number;mandatoryComplete:number;
}
export interface ReportFact {
  learnerId:number;name:string;unit:string;courseId:string;courseName:string;program:string;domain:string;
  mandatory:number;dueDate:string|null;completedAt:string|null;completed:number;status:string;attemptCount:number;
  average:number|null;everPassed:number;requiredPassed:number;passingScore:number;progress:number;
}
export type ReportParams = Record<string,string|number|boolean>;
/** [name, ...CHART_POINT_FIELDS counts, average]; see utils/reportGroups. */
export type ChartPoint = (string|number|null)[];
export interface ReportPage {
  asOf:string;summary?:ReportMetrics;choices?:Record<string,string[]>;items:(ReportFact|ReportMetrics)[];total:number;page:number;size:number;
  actions?:{id:string;count:number}[];charts?:Record<string,ReportMetrics[]>;
  /** Every group of each chart dimension in compact form, biggest first; charts holds only the first 20. */
  chartPoints?:Record<string,ChartPoint[]>;
  months?:{year:number;month:number;count:number}[];bins?:{name:string;count:number}[];
}
export const readTrainingReport = async(params:ReportParams) =>
  (await axiosClient.get<ReportPage>('/api/admin/reports/training/read',{params})).data;
export const readReportAttempts = async(learnerId:string,courseId:string,before?:string) =>
  (await axiosClient.get<{items:ReportingRecord['attempts'];nextCursor:string;hasMore:boolean}>('/api/admin/reports/training/attempts',{params:{learnerId,courseId,before,size:20}})).data;
export const exportTrainingReport = async(params:ReportParams) =>
  (await axiosClient.get<Blob>('/api/admin/reports/training/export',{params,responseType:'blob'})).data;
export interface ReportAttemptQuestion {
  id:number; text:string; options:string[]; selectedAnswers:number[]; correctAnswers:number[];
  answered:boolean; correct:boolean;
}
export interface ReportAttemptAnalysis {
  attemptId:string; available:boolean; source:'SNAPSHOT'|'CURRENT'|null; unavailableReason:'NO_ANSWERS'|'QUIZ_CHANGED'|null;
  correct:number; incorrect:number; unanswered:number; questions:ReportAttemptQuestion[];
}
export const readReportAttemptAnalysis = async(attemptId:string) =>
  (await axiosClient.get<ReportAttemptAnalysis>(`/api/admin/reports/training/attempts/${attemptId}/analysis`)).data;
