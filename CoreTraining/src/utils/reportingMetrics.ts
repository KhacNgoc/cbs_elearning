/** Reporting rules v1. The same raw-count aggregations drive every mockup screen. */
export interface ReportingRecord {
  learnerId: string;
  progress: number;
  passingScore: number;
  mandatory: boolean;
  dueDate: string | null;
  attempts: {score:number;takenAt:string;passingScore?:number;assessment?:string}[];
  completed?:boolean;
  started?:boolean;
  assessmentAverage?:number|null;
  assessmentPassed?:boolean;
  assessmentFirstPassed?:boolean;
}
export const reportingRules = [
  ['Phạm vi', 'Ngày lọc là ngày ghi danh. Trạng thái và kết quả chốt tại ngày dữ liệu; quá hạn là cờ bổ sung, có thể trùng với đang học.'],
  ['Hoàn thành', 'Một lượt học hoàn thành khi tiến độ bài học đạt 100% và có lần thi đạt ngưỡng riêng của bài. Tỷ lệ = lượt hoàn thành / lượt học trong phạm vi.'],
  ['Đạt kiểm tra', 'Tỷ lệ chính = lượt học đã từng thi đạt / lượt học có kiểm tra. Mỗi lượt học chỉ đếm một lần; chưa kiểm tra không tính vào mẫu số. Đạt lần đầu được báo riêng.'],
  ['Điểm trung bình', 'Lấy điểm gần nhất của mỗi lượt học, thang 100, rồi tính trung bình trên các lượt đã kiểm tra. Không lấy điểm cao nhất và không thay điểm thiếu bằng 0.'],
  ['Học viên quá hạn', 'Học viên có ít nhất một lượt chưa hoàn thành và đã quá hạn / học viên có ít nhất một lượt có hạn. Đếm người duy nhất trong phạm vi.'],
  ['Lượt học quá hạn', 'Lượt học chưa hoàn thành có hạn trước ngày chốt / tổng lượt học có hạn. Chỉ số này khác tỷ lệ học viên quá hạn và luôn ghi rõ tên.'],
  ['Tiến độ chương trình', 'Lượt khóa bắt buộc được giao đã hoàn thành / tổng lượt khóa bắt buộc được giao trong chương trình và phạm vi. Không trung bình phần trăm các khóa hoặc các nhóm.'],
  ['Gộp dữ liệu', 'Tính lại từ tử số và mẫu số gốc khi gộp đơn vị, nhóm hay chương trình. Không cộng số học viên giữa các nhóm và không trung bình các tỷ lệ đã làm tròn.'],
  ['Thiếu dữ liệu', 'Mẫu số bằng 0 hiển thị —. Không suy ra bỏ dở từ đang học; chưa có nhóm kiến thức và mục tiêu thì chưa tính điểm hoặc khoảng cách năng lực.'],
] as const;
export const ratio = (count:number,total:number):number|null => total ? count/total*100 : null;
export const formatRatio = (count:number,total:number) => total ? `${Math.round(count/total*100)}%` : '—';
export function latestScore(row:ReportingRecord):number|null {
  if(row.assessmentAverage!==undefined)return row.assessmentAverage;
  const latest = row.attempts.reduce<ReportingRecord['attempts'][number]|null>((best,a)=>!best||a.takenAt>=best.takenAt?a:best,null);
  return latest?.score??null;
}
export const hasPassed = (row:ReportingRecord) => row.assessmentPassed??row.attempts.some(a=>a.score>=row.passingScore);
export const firstAttemptPassed = (row:ReportingRecord) => {
  if(row.assessmentFirstPassed!==undefined)return row.assessmentFirstPassed;
  const first=row.attempts.reduce<ReportingRecord['attempts'][number]|null>((best,a)=>!best||a.takenAt<best.takenAt?a:best,null);
  return !!first&&first.score>=row.passingScore;
};
export const learningStatus = (row:ReportingRecord) => (row.completed??(row.progress===100&&hasPassed(row)))?'Hoàn thành':row.progress===0&&!row.attempts.length&&!row.started?'Chưa bắt đầu':'Đang học';
export const overdue = (row:ReportingRecord,asOf:string) => learningStatus(row)!=='Hoàn thành'&&!!row.dueDate&&row.dueDate<asOf;
export function summarize(records:ReportingRecord[],asOf:string) {
  const tested=records.filter(row=>row.attempts.length);
  const people=new Set(records.map(row=>row.learnerId));
  const evaluated=new Set(tested.map(row=>row.learnerId));
  const deadlines=records.filter(row=>row.dueDate);
  const overdueRecords=records.filter(row=>overdue(row,asOf));
  const mandatory=records.filter(row=>row.mandatory);
  const complete=records.filter(row=>learningStatus(row)==='Hoàn thành').length;
  const passed=tested.filter(hasPassed).length;
  const overduePeople=new Set(overdueRecords.map(row=>row.learnerId)).size;
  const peopleWithDeadline=new Set(deadlines.map(row=>row.learnerId)).size;
  const mandatoryComplete=mandatory.filter(row=>learningStatus(row)==='Hoàn thành').length;
  return {total:records.length,people:people.size,complete,learning:records.filter(row=>learningStatus(row)==='Đang học').length,notStarted:records.filter(row=>learningStatus(row)==='Chưa bắt đầu').length,tested:tested.length,passed,failed:tested.length-passed,firstPassed:tested.filter(firstAttemptPassed).length,average:tested.length?tested.reduce((sum,row)=>sum+latestScore(row)!,0)/tested.length:null,withDeadline:deadlines.length,overdue:overdueRecords.length,overduePeople,peopleWithDeadline,evaluatedPeople:evaluated.size,notEvaluatedPeople:people.size-evaluated.size,mandatory:mandatory.length,mandatoryComplete,completionRate:ratio(complete,records.length),passRate:ratio(passed,tested.length),overdueLearnerRate:ratio(overduePeople,peopleWithDeadline),programProgress:ratio(mandatoryComplete,mandatory.length)};
}
