import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';
import {parse,compileScript} from '@vue/compiler-sfc';
import ts from 'typescript';
const dom=new JSDOM('<div id="app"></div>');
for(const key of ['window','document','Element','HTMLElement','SVGElement','Node'])globalThis[key]=dom.window[key];
globalThis.requestAnimationFrame=callback=>setTimeout(callback,0);
const vue=await import('vue'); after(()=>dom.window.close());
test('profile info reads once and defers progress and workspace; results load on selection',async()=>{
 const calls={profile:0,progress:0,workspace:0,results:0,history:0,stats:0,courses:0};const stub={render(){return vue.h('div')}};
 const {descriptor}=parse(await readFile(new URL('../src/views/Profile.vue',import.meta.url),'utf8'));
 let code=ts.transpileModule(compileScript(descriptor,{id:'profile-read-test',inlineTemplate:true}).content,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,bindings,module)=>`const ${bindings.replace(/\bas\b/g,':')} = modules[${JSON.stringify(module)}];`).replace('export default','return');
 const route=vue.reactive({query:{}});
 const modules={vue,'vue-router':{useRoute:()=>route,useRouter:()=>({replace:async()=>{}})},'lucide-vue-next':{Award:stub},'../stores/userStore':{useUserStore:()=>({fetchProgress:async()=>{calls.progress++;calls.profile++},points:0,unlockedBadges:[]})},'../stores/learningStore':{useLearningStore:()=>({load:async()=>calls.workspace++})},'../api/userApi':{userApi:{getMyProfile:async()=>{calls.profile++;return {username:'learner',fullName:'Learner',role:'ROLE_USER'}}}},'../api/profileReadApi':{profileReadApi:{results:async()=>{calls.results++;return {items:[{latest:{quizId:'quiz',revision:1,title:'Quiz',courseId:'c',score:1,total:2,takenAt:'2026-01-01'},best:{score:2,total:2},attemptCount:25,courseName:'Course'}],total:1,legacyScores:[]}},history:async()=>{calls.history++;return {items:[],nextCursor:null}},stats:async()=>{calls.stats++;return {joined:100,competencies:[]}},courses:async()=>{calls.courses++;return {items:[],total:0}}}},'../utils/permissions':{roleLabel:()=>''},'../utils/learning':await import('../src/utils/learning.ts')};
 for(const name of ['CourseThumbnail','RadarChart','ProgressBar','TablePagination','PageLoading']) modules[`../components/${name}.vue`]=stub;
 const Page=new Function('modules',code)(modules);const app=vue.createApp(Page);app.component('RouterLink',stub);app.mount('#app');
 const flush=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await vue.nextTick()}};await flush();await new Promise(resolve=>setTimeout(resolve,100));await flush();
 try{assert.deepEqual(calls,{profile:1,progress:0,workspace:0,results:0,history:0,stats:0,courses:0});document.querySelector('#profile-results-tab').click();await flush();assert.equal(calls.results,1);assert.equal(calls.history,0);document.querySelector('.results-table .view-link').click();await flush();assert.equal(calls.history,1);document.querySelector('#profile-stats-tab').click();await flush();assert.equal(calls.stats,1);assert.equal(calls.courses,1);}finally{app.unmount()}
});
