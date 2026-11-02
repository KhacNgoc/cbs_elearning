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
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,b,m)=>`const ${b}=modules[${JSON.stringify(m)}];`).replace('export const useQuizStore','const useQuizStore');
 const pinia=await import('pinia');pinia.setActivePinia(pinia.createPinia());
 const quiz=new Function('modules',code+'\nreturn useQuizStore();')({'pinia':pinia,'../api/quizApi':{quizApi:{getQuizQuestions:async(c,l)=>l==='old'?old:[{id:2}]}},'./userStore':{useUserStore:()=>({})}});
 const pending=quiz.startQuiz('c','old');await quiz.startQuiz('c','new');resolveOld([{id:1}]);await pending;
 try{assert.equal(quiz.questions[0].id,2)}finally{quiz.stopTimer()}
});
