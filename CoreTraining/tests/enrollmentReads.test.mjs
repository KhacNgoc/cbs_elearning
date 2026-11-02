import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
const dom=new JSDOM('<div id="app"></div>');
for(const k of ['window','document','Element','HTMLElement','SVGElement','Node'])globalThis[k]=dom.window[k];
const vue=await import('vue');
const flush=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await vue.nextTick();}};
test('enrollment page requests only its cursor list, next page and debounced filter',async()=>{
 const calls=[];
 const {descriptor}=parse(await readFile(new URL('../src/views/EnrollmentAdmin.vue',import.meta.url),'utf8'));
 let code=ts.transpileModule(compileScript(descriptor,{id:'enrollments',inlineTemplate:true}).content,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,b,id)=>`const ${b.startsWith('{')?b.replace(/\bas\b/g,':'):`{default:${b}}`}=modules[${JSON.stringify(id)}];`).replace('export default','return');
 const modules={vue,'../api/adminListApi':{adminListApi:{enrollments:async p=>{calls.push(p);return {items:[],nextCursor:'next',hasMore:true};}}},'../api/adminApi':{adminApi:{}},'../stores/userStore':{useUserStore:()=>({can:()=>true})},'../utils/toast':{showToast(){}},'../components/AdminModal.vue':{default:{render:()=>null}}};
 const component=new Function('modules',code)(modules),app=vue.createApp(component);
 try{app.mount('#app');await flush();assert.equal(calls.length,1);assert.equal(calls[0].size,20);document.querySelector('[aria-label="Trang sau"]').click();await flush();assert.equal(calls[1].cursor,'next');const input=document.querySelector('input');input.value='learner';input.dispatchEvent(new dom.window.Event('input'));await new Promise(r=>setTimeout(r,300));await flush();assert.equal(calls.at(-1).query,'learner');assert.equal(calls.at(-1).cursor,undefined);}finally{app.unmount();dom.window.close();}
});
