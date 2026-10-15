import { axiosClient } from './axiosClient';
import type { ReportingRecord } from '../utils/reportingMetrics';
export interface ReportRow extends ReportingRecord {
  id:string; name:string; unit:string; group:string; courseId:string; courseName:string;
  program:string; domain:string; completedAt:string|null; date:string;
  status:string; score:number|null; passed:boolean;
}
export async function getTrainingReport():Promise<{asOf:string;rows:ReportRow[]}> {
  const {data}=await axiosClient.get('/api/admin/reports/training');
  return {asOf:data.asOf,rows:data.rows};
}
