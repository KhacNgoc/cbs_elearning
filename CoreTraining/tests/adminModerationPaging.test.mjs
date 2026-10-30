import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
const dom = new JSDOM('<!doctype html><div id="app"></div>');
for (const key of ['window','document','Element','HTMLElement','SVGElement','Node']) globalThis[key]=dom.window[key];
const vue=await import('vue');
after(()=>dom.window.close());
const flush=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await vue.nextTick();}};
async function mount() {
 const {descriptor}=parse(await readFile(new URL('../src/views/ContentReports.vue',import.meta.url),'utf8'));
 let code=ts.transpileModule(compileScript(descriptor,{id:'admin-reports',inlineTemplate:true}).content,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,bindings,module)=>`const ${bindings.replace(/\bas\b/g,':')} = modules[${JSON.stringify(module)}];`).replace('export default','return');
 const calls=[];
 const {useTablePagination}=await import('../src/composables/useTablePagination.ts');
 const component=new Function('modules',code)({vue,'vue-router':{useRoute:()=>({query:{}})},
 '../api/contentToolsApi':{contentToolsApi:{reports:async()=>[]},contentToolsError:()=> 'error',reportStatus:{NEW:'Mới gửi'}},
 '../api/adminListApi':{adminListApi:{reports:async params=>{calls.push({...params});return {items:[],hasMore:calls.length===1,nextCursor:'next',newCount:27};}}},
 '../components/AdminModal.vue':{render:()=>null},'../components/ProtectedImage.vue':{render:()=>null},
 '../components/TablePagination.vue':{render:()=>null},'../composables/useTablePagination':{useTablePagination},'../utils/toast':{showToast(){}}});
 const app=vue.createApp(component);app.mount('#app');await flush();return{app,calls};
}
test('moderation mounts one bounded request, complete-scope new count, and next cursor',async()=>{
 const f=await mount();try{
 assert.equal(f.calls.length,1);assert.equal(f.calls[0].size,10);assert.match(document.body.textContent,/27 báo cáo mới/);
 [...document.querySelectorAll('button')].find(b=>b.textContent==='Trang sau').click();await flush();
 assert.equal(f.calls.at(-1).cursor,'next');
 }finally{f.app.unmount();}
});
