import test from 'node:test';
import assert from 'node:assert/strict';
import { learningSummary, groupAttempts, competencyScores, normalizeSearch, deadlineState, resumeRoute } from '../src/utils/learning.ts';

const course=(id,source,total,completed,extra={})=>({course:{id,name:id,domainId:id,domainName:id},source,totalCount:total,completedCount:completed,learningStatus:completed===total?'COMPLETED':'IN_PROGRESS',mandatory:false,...extra});
const attempt=(id,score,total,date,revision=1)=>({id,quizId:'Q1',courseId:'A',lessonId:'L1',title:'Đánh giá',domainId:'A',domainName:'A',score,total,takenAt:date,revision,passingScore:80,passed:score/total>=.8});
test('catalog and pending requests do not affect completion denominator',()=>{
  const summary=learningSummary([course('A','ASSIGNED',8,3,{mandatory:true}),course('B','REGISTERED',4,4),course('C','AVAILABLE',100,0),course('D','PENDING',100,0)]);
  assert.equal(summary.percent,58);assert.equal(summary.joined.length,2);assert.equal(summary.complete,1);assert.equal(summary.mandatory,1);
  assert.equal(learningSummary([]).percent,null);
});
test('latest and highest scores differ and different revisions never merge',()=>{
  const groups=groupAttempts([attempt('1',8,10,'2026-10-01'),attempt('2',7,10,'2026-10-02'),attempt('3',10,20,'2026-10-03',2)]);
  assert.equal(groups.length,2);assert.equal(groups[0].latest.score,7);assert.equal(groups[0].best.score,8);
  const unequal=groupAttempts([attempt('1',9,20,'2026-10-01'),attempt('2',8,10,'2026-10-02')]);assert.equal(unequal[0].best.id,'2');
});
test('competency does not convert progress to scores or double-count quiz revisions',()=>{
  const rows=competencyScores([course('A','ASSIGNED',10,9),course('B','REGISTERED',10,10)], [attempt('1',8,10,'2026-10-01'),attempt('2',10,20,'2026-10-03',2)]);
  assert.equal(rows[0].value,50);assert.equal(rows[0].count,1);assert.equal(rows[1].value,null);
});
test('search normalizes Vietnamese and deadlines use the end of the Vietnam day',()=>{
  assert.equal(normalizeSearch('TIỀN VAY Đầu tư'),'tien vay dau tu');
  const item=course('A','ASSIGNED',5,1,{dueDate:'2026-10-14'});
  assert.equal(deadlineState(item,Date.parse('2026-10-14T23:00:00+07:00')),'Sắp đến hạn');
  assert.equal(deadlineState(item,Date.parse('2026-10-15T00:00:00+07:00')),'Quá hạn');
  assert.equal(deadlineState({...item,learningStatus:'COMPLETED'},Date.parse('2026-10-15')),'');
});
test('resume keeps document identity and routes required assessment to the lesson quiz',()=>{
  const target={courseId:'C',lessonId:'L',contentType:'DOCUMENT',documentId:12};
  assert.deepEqual(resumeRoute(target),{path:'/lesson/L',query:{documentId:'12'}});
  assert.deepEqual(resumeRoute({...target,contentType:'QUIZ'}),{path:'/quiz/C',query:{lessonId:'L'}});
});
