import test from 'node:test';
import assert from 'node:assert/strict';
import { summarizeCourseRatings } from '../src/utils/courseRatingMetrics.ts';
const enrollments=[{learnerId:'A',courseName:'C1'},{learnerId:'B',courseName:'C1'},{learnerId:'A',courseName:'C2'}];
const review=(patch={})=>({id:'r1',learnerId:'A',courseName:'C1',stars:5,content:'Mẫu',createdAt:'2026-10-01',scope:'COURSE',parentId:null,...patch});
test('one latest valid course rating per learner/course; replies and lessons excluded',()=>{
  const result=summarizeCourseRatings(enrollments,[review(),review({id:'r2',stars:2,createdAt:'2026-10-02'}),review({scope:'LESSON',learnerId:'B'}),review({parentId:'r1',learnerId:'B'}),review({id:'r3',learnerId:'B',stars:0}),review({id:'future',learnerId:'B',createdAt:'2026-11-01'})],'2026-10-15');
  assert.equal(result.count,1);assert.equal(result.average,2);assert.equal(result.low,1);
  assert.equal(result.eligible,3);assert.ok(Math.abs(result.responseRate-100/3)<1e-10);
});
test('global rating is weighted by reviews, not mean of course averages',()=>{
  const result=summarizeCourseRatings(enrollments,[review(),review({id:'b',learnerId:'B'}),review({id:'c',courseName:'C2',stars:1})],'2026-10-15');
  assert.equal(result.average,11/3);assert.equal(result.responseRate,100);
  assert.equal(result.distribution.reduce((sum,b)=>sum+b.count,0),3);
});
test('missing ratings stay missing, and scope limits the eligible population',()=>{
  assert.equal(summarizeCourseRatings(enrollments,[],'2026-10-15').average,null);
  assert.equal(summarizeCourseRatings([],[],'2026-10-15').responseRate,null);
  assert.equal(summarizeCourseRatings([], [review()], '2026-10-15').count,0);
});
