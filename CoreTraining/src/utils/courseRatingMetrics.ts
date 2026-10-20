export interface CourseRating {
  id:string;learnerId:string;courseName:string;stars:number|null;content:string;createdAt:string;
  scope:'COURSE'|'LESSON';parentId:string|null;
}
/** Latest valid course rating per learner/course. Excludes lesson comments and replies. */
export function summarizeCourseRatings(enrollments:{learnerId:string;courseName:string}[],ratings:CourseRating[],asOf:string){
  const key=(row:{learnerId:string;courseName:string})=>JSON.stringify([row.learnerId,row.courseName]);
  const eligible=new Set(enrollments.map(key));
  const latest=new Map<string,CourseRating>();
  ratings.filter(r=>r.scope==='COURSE'&&!r.parentId&&Number.isInteger(r.stars)&&r.stars!>=1&&r.stars!<=5&&r.createdAt.slice(0,10)<=asOf&&eligible.has(key(r))).forEach(r=>{
    const previous=latest.get(key(r));
    if(!previous||r.createdAt>previous.createdAt||(r.createdAt===previous.createdAt&&r.id>previous.id))latest.set(key(r),r);
  });
  const reviews=[...latest.values()].sort((a,b)=>b.createdAt.localeCompare(a.createdAt));
  return {reviews,count:reviews.length,eligible:eligible.size,average:reviews.length?reviews.reduce((n,r)=>n+r.stars!,0)/reviews.length:null,responseRate:eligible.size?reviews.length/eligible.size*100:null,low:reviews.filter(r=>r.stars!<=2).length,distribution:[5,4,3,2,1].map(stars=>({stars,count:reviews.filter(r=>r.stars===stars).length}))};
}
