import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
import * as metrics from '../src/utils/reportingMetrics.ts';
import * as views from '../src/utils/reportingViews.ts';

const dom = new JSDOM('<div id="app"></div>');
for(const key of ['window','document','Element','HTMLElement','SVGElement','Node'])globalThis[key]=dom.window[key];
const vue=await import('vue'), icons=await import('lucide-vue-next');
const { useTablePagination }=await import('../src/composables/useTablePagination.ts');
const flush=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await vue.nextTick();}};
test('report displays full-scope totals while requesting one database page',async()=>{
 const calls=[],legacy=[];
 const summary={total:51,people:40,complete:20,learning:10,notStarted:21,tested:0,passed:0,failed:0,firstPassed:0,average:null,withDeadline:0,overdue:0,overduePeople:0,peopleWithDeadline:0,mandatory:0,mandatoryComplete:0};
 const source=await readFile(new URL('../src/views/ReportsMockup.vue',import.meta.url),'utf8');
 const {descriptor}=parse(source);let code=ts.transpileModule(compileScript(descriptor,{id:'report-read',inlineTemplate:true}).content,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,bindings,id)=>`const ${bindings.startsWith('{')?bindings.replace(/\bas\b/g,':'):`{default:${bindings}}`}=modules[${JSON.stringify(id)}];`).replace('export default','return');
 const stub={render:()=>vue.h('div')};
 const modules={vue,'lucide-vue-next':icons,'vue-router':{useRoute:()=>({query:{},path:'/admin'}),useRouter:()=>({replace:async()=>{}})},'../utils/reportingMetrics':metrics,'../utils/reportingViews':views,'../composables/useTablePagination':{useTablePagination},'../utils/scrollWithin':{scrollWithin(){}},'../api/trainingReportApi':{
  getTrainingReport:async()=>{legacy.push(1);return{asOf:'2026-10-28',rows:[]};},
  readTrainingReport:async params=>{calls.push(params);return{asOf:'2026-10-28',summary,choices:{units:[],courses:[],programs:[]},items:[],total:51,page:params.page,size:20,charts:{},actions:[],months:[],bins:[]};},
 }};
 modules['../components/TablePagination.vue']={default:{props:['total','page'],emits:['update:page'],template:'<button class="next" @click="$emit(\'update:page\',page+1)">{{total}}</button>'}};
 const component=new Function('modules',code)(new Proxy(modules,{get:(t,k)=>t[k]||{default:stub}}));
 const app=vue.createApp(component);try{app.mount('#app');await flush();assert.equal(legacy.length,0);assert.equal(calls.length,1);assert.match(document.body.textContent,/51 lượt học/);document.querySelector('.next').click();await new Promise(r=>setTimeout(r,300));await flush();assert.equal(calls.at(-1).page,1);assert.equal(calls.at(-1).metadata,false);const count=calls.length;const date=document.querySelector('input[type=date]');date.value='2099-01-01';date.dispatchEvent(new dom.window.Event('input'));await new Promise(r=>setTimeout(r,300));await flush();assert.equal(calls.length,count);assert.equal(document.querySelectorAll('.kpi').length,0);date.value='';date.dispatchEvent(new dom.window.Event('input'));await new Promise(r=>setTimeout(r,300));await flush();assert.equal(document.querySelectorAll('.kpi').length,5);}finally{app.unmount();dom.window.close();}
});
