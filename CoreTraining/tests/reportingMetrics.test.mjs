import test from 'node:test';
import assert from 'node:assert/strict';
import { summarize, latestScore, hasPassed, learningStatus, firstAttemptPassed, formatRatio, overdue } from '../src/utils/reportingMetrics.ts';
const asOf='2026-10-15';
const sample=(patch={})=>({learnerId:'A',progress:0,passingScore:80,mandatory:true,dueDate:'2026-10-12',attempts:[],...patch});
const rows=[
  sample({progress:100,attempts:[{score:100,takenAt:'2026-10-01'},{score:40,takenAt:'2026-10-04'}]}),
  sample(),
  sample({learnerId:'B',progress:40,attempts:[{score:90,takenAt:'2026-10-03'}]}),
  sample({learnerId:'C',mandatory:false,dueDate:null}),
];

test('database completion supports courses without exams and incomplete required exams',()=>{
  const completed=sample({progress:100,completed:true,assessmentAverage:null,assessmentPassed:false,assessmentFirstPassed:false});
  assert.equal(learningStatus(completed),'Hoàn thành');
  assert.equal(overdue(completed,asOf),false);
  assert.equal(summarize([completed],asOf).complete,1);
  assert.equal(summarize([completed],asOf).average,null);
  const incomplete=sample({progress:100,completed:false,started:true,attempts:[{score:100,takenAt:asOf}],assessmentAverage:50,assessmentPassed:false,assessmentFirstPassed:false});
  assert.equal(learningStatus(incomplete),'Đang học');
  assert.equal(latestScore(incomplete),50);
  assert.equal(hasPassed(incomplete),false);
  assert.equal(firstAttemptPassed(incomplete),false);
});
test('latest score and ever-passed measure different facts',()=>{
  assert.equal(latestScore(rows[0]),40);
  assert.equal(hasPassed(rows[0]),true);
  assert.equal(learningStatus(rows[0]),'Hoàn thành');
  assert.equal(summarize(rows,asOf).average,65);
  assert.equal(summarize(rows,asOf).passRate,100);
});
test('no exam is excluded from grade and pass denominators',()=>{
  const metrics=summarize(rows,asOf);
  assert.equal(metrics.total,4);assert.equal(metrics.tested,2);
  assert.equal(metrics.complete,1);assert.equal(metrics.completionRate,25);
  assert.equal(summarize([],asOf).average,null);
  assert.equal(summarize([],asOf).passRate,null);
  assert.equal(formatRatio(0,0),'—');
});
test('distinct overdue learners are not overdue enrollments',()=>{
  const metrics=summarize([...rows,sample()],asOf);
  assert.equal(metrics.overdue,3);assert.equal(metrics.overduePeople,2);
  assert.equal(metrics.peopleWithDeadline,2);assert.equal(metrics.overdueLearnerRate,100);
  assert.equal(overdue(sample({dueDate:asOf}),asOf),false);
});
test('program progress excludes optional assigned courses',()=>{
  const metrics=summarize(rows,asOf);
  assert.equal(metrics.mandatory,3);assert.equal(metrics.mandatoryComplete,1);
  assert.ok(Math.abs(metrics.programProgress-100/3)<1e-10);
});
test('rollups use original counts, not mean of rounded group percentages',()=>{
  const a=summarize(rows.filter(r=>r.learnerId==='A'),asOf);
  const others=summarize(rows.filter(r=>r.learnerId!=='A'),asOf);
  assert.equal(formatRatio(a.complete+others.complete,a.total+others.total),formatRatio(summarize(rows,asOf).complete,rows.length));
  assert.equal(summarize(rows,asOf).people,3);
});
test('attempt order does not change first or latest result',()=>{
  const reverse={...rows[0],attempts:[...rows[0].attempts].reverse()};
  assert.equal(latestScore(reverse),40);assert.equal(firstAttemptPassed(reverse),true);
});
