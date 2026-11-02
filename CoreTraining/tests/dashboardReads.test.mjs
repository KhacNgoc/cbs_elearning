import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import ts from 'typescript';
import {createPinia,setActivePinia,defineStore} from 'pinia';
const deferred=()=>{let resolve;return {promise:new Promise(r=>resolve=r),resolve:(v)=>resolve(v)}};
async function storeFixture(api) {
 let code=ts.transpileModule(await readFile(new URL('../src/stores/learningStore.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,b,m)=>`const ${b}=modules[${JSON.stringify(m)}];`).replace('export const useLearningStore','const useLearningStore');
 setActivePinia(createPinia());
 return new Function('modules',code+'\nreturn useLearningStore();')({'pinia':{defineStore},'../api/axiosClient':{axiosClient:{get(){throw new Error('legacy workspace must not load')}}},'../api/learnerDashboardApi':api});
}
test('dashboard reads bounded cards and complete summary without workspace, and refreshes choices on tab change',async()=>{
 const calls=[];
 const store=await storeFixture({getDashboardCourses:async p=>{calls.push(['page',p]);return {items:[],total:100}},getDashboardSummary:async tab=>{calls.push(['summary',tab]);return {summary:{joined:100},choices:{},resume:null}}});
 await store.loadDashboard({tab:'mine',page:1,size:6});
 assert.equal(store.dashboard.summary.joined,100);assert.deepEqual(calls.map(c=>c[0]),['page','summary']);
 await store.loadDashboard({tab:'mine',page:2,size:6},false);assert.equal(calls.length,3);
 await store.loadDashboard({tab:'discover',page:1,size:6},false);assert.deepEqual(calls.at(-1),['summary','discover']);
 assert.equal(store.workspace,null);
});
test('dashboard ignores late page/summary reads and invalidates in-flight reads when filters change',async()=>{
 const old=deferred();let count=0;
 const store=await storeFixture({getDashboardCourses:async()=>++count===1?old.promise:{items:[{course:{id:'new'}}],total:1},getDashboardSummary:async()=>({summary:{joined:1},choices:{}})});
 const first=store.loadDashboard({tab:'mine',search:'old'});store.invalidateDashboard();
 await store.loadDashboard({tab:'mine',search:'new'});old.resolve({items:[{course:{id:'old'}}],total:50});await first;
 assert.equal(store.dashboardPage.items[0].course.id,'new');assert.equal(store.dashboardPage.total,1);
});
