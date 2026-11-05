import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';
import {parse,compileScript} from '@vue/compiler-sfc';
import ts from 'typescript';
const dom=new JSDOM('<div id="app"></div>');
for(const key of ['window','document','Element','HTMLElement','SVGElement','Node'])globalThis[key]=dom.window[key];
const vue=await import('vue');after(()=>dom.window.close());
const flush=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await vue.nextTick()}};
test('direct quiz reads targeted context without catalog/progress and reloads on reused route',async()=>{
 const calls={context:[],catalog:0,progress:0,quiz:[]};
 const {descriptor}=parse(await readFile(new URL('../src/views/QuizView.vue',import.meta.url),'utf8'));
 let code=ts.transpileModule(compileScript(descriptor,{id:'learner-quiz',inlineTemplate:true}).content,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,b,m)=>`const ${b.replace(/\bas\b/g,':')}=modules[${JSON.stringify(m)}];`).replace('export default','return');
 const route=vue.reactive({query:{lessonId:'l1'}});
 const stub={render(){return vue.h('div')}};
 const quiz=vue.reactive({quizQuestions:[],selectedAnswers:{},isQuizFinished:false,stopTimer(){},startQuiz:async(c,l)=>calls.quiz.push([c,l])});
 const modules={vue,'vue-router':{useRoute:()=>route,useRouter:()=>({push(){}})},'../stores/courseStore':{useCourseStore:()=>({courses:[],fetchCourses:async()=>calls.catalog++})},'../stores/quizStore':{useQuizStore:()=>quiz},'../stores/userStore':{useUserStore:()=>({mandatoryCourses:[],fetchProgress:async()=>calls.progress++,isCourseAccessible:()=>true,lessonQuizScores:{},lessonQuizTotals:{},quizScores:{},quizTotals:{},unlockedBadges:[]})},'../api/quizApi':{quizApi:{}},'../api/learnerQuizApi':{getLearnerQuizContext:async(c,l)=>{calls.context.push([c,l]);return {id:c,name:'Target course',quizPassingScore:65,lessonId:l,score:null,total:null}}},'lucide-vue-next':{Award:stub,CheckCircle2:stub,Shield:stub,Medal:stub,XCircle:stub}};
 const app=vue.createApp(new Function('modules',code)(modules),{courseId:'c1'});app.component('RouterLink',stub);app.mount('#app');await flush();
 try {assert.deepEqual(calls.context,[['c1','l1']]);assert.equal(calls.catalog,0);assert.equal(calls.progress,0);assert.deepEqual(calls.quiz,[['c1','l1']]);route.query.lessonId='l2';await flush();assert.deepEqual(calls.context.at(-1),['c1','l2']);}
 finally {app.unmount()}
});
test('quiz store ignores older questions when switching to another lesson',async()=>{
 let resolveOld;const old=new Promise(r=>resolveOld=r);
 let code=ts.transpileModule(await readFile(new URL('../src/stores/quizStore.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,b,m)=>`const ${b}=modules[${JSON.stringify(m)}];`).replace('export const useQuizStore','const useQuizStore').replace('export const QUIZ_DRAFT_PREFIX','const QUIZ_DRAFT_PREFIX');
 const pinia=await import('pinia');pinia.setActivePinia(pinia.createPinia());
 const quiz=new Function('modules',code+'\nreturn useQuizStore();')({'pinia':pinia,'../api/quizApi':{quizApi:{getQuizQuestions:async(c,l)=>l==='old'?old:[{id:2}],startLessonQuiz:async()=>({sessionId:null,timeLimitMinutes:null,remainingSeconds:null})}},'./userStore':{useUserStore:()=>({})}});
 const pending=quiz.startQuiz('c','old');await quiz.startQuiz('c','new');resolveOld([{id:1}]);await pending;
 try{assert.equal(quiz.questions[0].id,2)}finally{quiz.stopTimer()}
});
test('timed quiz store resumes the server deadline and submits its session id',async()=>{
 let code=ts.transpileModule(await readFile(new URL('../src/stores/quizStore.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,b,m)=>`const ${b}=modules[${JSON.stringify(m)}];`).replace('export const useQuizStore','const useQuizStore').replace('export const QUIZ_DRAFT_PREFIX','const QUIZ_DRAFT_PREFIX');
 const pinia=await import('pinia');pinia.setActivePinia(pinia.createPinia());
 let submitted;
 const quiz=new Function('modules',code+'\nreturn useQuizStore();')({'pinia':pinia,'../api/quizApi':{quizApi:{
  getQuizQuestions:async()=>[{id:1}],
  startLessonQuiz:async()=>({sessionId:'s1',timeLimitMinutes:10,remainingSeconds:540}),
  submitQuiz:async(c,data)=>{submitted=data;return {score:0,total:1,percent:0,passed:false}}}},
  './userStore':{useUserStore:()=>({fetchProgress:async()=>{}})}});
 await quiz.startQuiz('c','l');
 try{
  assert.equal(quiz.timeLimitSeconds,600);assert.equal(quiz.remainingSeconds,540);assert.equal(quiz.formattedRemaining,'09:00');
  await quiz.submitQuiz();
  assert.equal(submitted.sessionId,'s1');assert.equal(quiz.isQuizFinished,true);
 }finally{quiz.stopTimer()}
});
test('quiz answers survive a reload via localStorage and are cleared after submit',async()=>{
 const memory=new Map();
 const storage={getItem:k=>memory.has(k)?memory.get(k):null,setItem:(k,v)=>memory.set(k,String(v)),removeItem:k=>memory.delete(k)};
 const previous=Object.getOwnPropertyDescriptor(globalThis,'localStorage');
 Object.defineProperty(globalThis,'localStorage',{value:storage,configurable:true,writable:true});
 let code=ts.transpileModule(await readFile(new URL('../src/stores/quizStore.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,b,m)=>`const ${b}=modules[${JSON.stringify(m)}];`).replace('export const useQuizStore','const useQuizStore').replace('export const QUIZ_DRAFT_PREFIX','const QUIZ_DRAFT_PREFIX');
 const pinia=await import('pinia');
 let sessionId='s1';
 const make=()=>{pinia.setActivePinia(pinia.createPinia());return new Function('modules',code+'\nreturn useQuizStore();')({'pinia':pinia,'../api/quizApi':{quizApi:{
  getQuizQuestions:async()=>[{id:1,options:['A','B']},{id:2,options:['A','B']}],
  startLessonQuiz:async()=>({sessionId,timeLimitMinutes:10,remainingSeconds:500}),
  submitQuiz:async()=>({score:2,total:2,percent:100,passed:true})}},
  './userStore':{useUserStore:()=>({username:'u1',fetchProgress:async()=>{}})}});};
 try{
  let quiz=make();await quiz.startQuiz('c','l');
  quiz.selectAnswer(1,1);quiz.nextQuestion();quiz.stopTimer();
  assert.equal(memory.size,1);assert.match([...memory.keys()][0],/^quiz-draft:u1:c:l$/);
  quiz=make();await quiz.startQuiz('c','l');quiz.stopTimer();
  assert.deepEqual(quiz.selectedAnswers,{1:[1]});assert.equal(quiz.currentQuestionIndex,1);
  quiz.selectAnswer(2,0);await quiz.submitQuiz();
  assert.equal(memory.size,0);
  // A draft from an earlier, expired server session is not carried into a new attempt.
  quiz=make();await quiz.startQuiz('c','l');quiz.selectAnswer(1,0);quiz.stopTimer();
  sessionId='s2';quiz=make();await quiz.startQuiz('c','l');quiz.stopTimer();
  assert.deepEqual(quiz.selectedAnswers,{});assert.equal(memory.size,0);
 }finally{
  if(previous)Object.defineProperty(globalThis,'localStorage',previous);else delete globalThis.localStorage;
 }
});
