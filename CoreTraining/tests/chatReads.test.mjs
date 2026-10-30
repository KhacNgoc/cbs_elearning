import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';
import {parse,compileScript} from '@vue/compiler-sfc';
import ts from 'typescript';
const dom=new JSDOM('<div id="app"></div><div id="learning-chatbot-launcher"></div>');for(const key of ['window','document','Element','HTMLElement','SVGElement','Node'])globalThis[key]=dom.window[key];globalThis.requestAnimationFrame=cb=>setTimeout(cb,0);
const vue=await import('vue');const icons=await import('lucide-vue-next');after(()=>dom.window.close());
const flush=async()=>{for(let i=0;i<30;i++){await Promise.resolve();await vue.nextTick()}};
test('chat opens with empty catalog, asks server for choices and prepends complete older exchanges',async()=>{
 const calls={choices:0,history:[]};const row=(id,question)=>({id,question,answer:'Answer',courseId:'c',conversationId:'conversation',sources:[],createdAt:'2026-01-01T00:00:00Z'});
 const modules={vue,'lucide-vue-next':icons,'vue-router':{useRoute:()=>({name:'Dashboard'})},'../stores/courseStore':{useCourseStore:()=>({courses:[],currentCourse:null})},'../api/axiosClient':{isTimeoutError:()=>false},'../api/ragApi':{ragApi:{courseChoices:async()=>{calls.choices++;return [{id:'c',name:'Server course'}]},getHistoryPage:async(course,conversation,before)=>{calls.history.push({course,conversation,before});return before?{items:[row(1,'Older question')],nextBeforeId:null}:{items:[row(2,'Recent question')],nextBeforeId:2}}}}};
 const {descriptor}=parse(await readFile(new URL('../src/components/LearningChatbot.vue',import.meta.url),'utf8'));let code=ts.transpileModule(compileScript(descriptor,{id:'chat-read-test',inlineTemplate:true}).content,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,bindings,module)=>`const ${bindings.replace(/\bas\b/g,':')} = modules[${JSON.stringify(module)}];`).replace('export default','return');const Page=new Function('modules',code)(modules),app=vue.createApp(Page,{locked:false});app.mount('#app');
 try{assert.equal(calls.choices,0);document.querySelector('.chat-launcher').click();await flush();assert.equal(calls.choices,1);assert.equal(calls.history.length,1);assert.equal(document.querySelector('#chat-course option').textContent,'Server course');[...document.querySelectorAll('button')].find(b=>b.textContent.includes('Tải trao đổi trước')).click();await flush();assert.equal(calls.history[1].before,2);const text=document.querySelector('.chat-body').textContent;assert.ok(text.indexOf('Older question')<text.indexOf('Recent question'));}finally{app.unmount()}
});
