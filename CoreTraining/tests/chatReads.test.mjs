import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';
import {parse,compileScript} from '@vue/compiler-sfc';
import ts from 'typescript';
const dom=new JSDOM('<div id="app"></div><div id="learning-chatbot-launcher"></div>');for(const key of ['window','document','Element','HTMLElement','SVGElement','Node'])globalThis[key]=dom.window[key];globalThis.requestAnimationFrame=cb=>setTimeout(cb,0);
const vue=await import('vue');const icons=await import('lucide-vue-next');after(()=>dom.window.close());
const flush=async()=>{for(let i=0;i<30;i++){await Promise.resolve();await vue.nextTick()}};
const row=(id,question,sources=[])=>({id,question,answer:'Answer',courseId:null,conversationId:'conversation',sources,createdAt:'2026-01-01T00:00:00Z'});
async function mountChat({routeName='Dashboard',currentCourse=null,history,chat}){
 document.body.innerHTML='<div id="app"></div><div id="learning-chatbot-launcher"></div>';
 const modules={vue,'lucide-vue-next':icons,'vue-router':{useRoute:()=>({name:routeName})},'../stores/courseStore':{useCourseStore:()=>({courses:[],currentCourse})},'../api/axiosClient':{isTimeoutError:()=>false},'../api/ragApi':{ragApi:{getHistoryPage:history,chat}}};
 const {descriptor}=parse(await readFile(new URL('../src/components/LearningChatbot.vue',import.meta.url),'utf8'));let code=ts.transpileModule(compileScript(descriptor,{id:'chat-read-test',inlineTemplate:true}).content,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
 code=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,bindings,module)=>`const ${bindings.replace(/\bas\b/g,':')} = modules[${JSON.stringify(module)}];`).replace('export default','return');
 const app=vue.createApp(new Function('modules',code)(modules),{locked:false});app.mount('#app');return app;
}
test('chat opens without a course picker and prepends complete older exchanges',async()=>{
 const calls=[];
 const app=await mountChat({history:async(conversation,before)=>{calls.push({conversation,before});return before?{items:[row(1,'Older question')],nextBeforeId:null}:{items:[row(2,'Recent question',[{documentId:'d',documentName:'Doc',page:1,section:'S',courseId:'c2',courseName:'Tiền gửi'}])],nextBeforeId:2}}});
 try{assert.equal(calls.length,0);document.querySelector('.chat-launcher').click();await flush();
  assert.equal(calls.length,1);assert.equal(calls[0].conversation,undefined);assert.equal(document.querySelector('#chat-course'),null);assert.equal(document.querySelector('.chat-context'),null);
  assert.equal(document.querySelector('.source-course').textContent,'Tiền gửi');
  scrollChatToTop();await flush();
  assert.deepEqual(calls[1],{conversation:'conversation',before:2});const text=document.querySelector('.chat-body').textContent;assert.ok(text.indexOf('Older question')<text.indexOf('Recent question'));
  scrollChatToTop();await flush();assert.equal(calls.length,2,'the last page was reached');
 }finally{try{app.unmount()}catch{}}
});
const scrollChatToTop=()=>{const body=document.querySelector('.chat-body');body.scrollTop=0;body.dispatchEvent(new window.Event('scroll'))};
test('scrolling up loads one older page at a time and offers a retry after a failure',async()=>{
 const calls=[];let release,fail=false;
 const app=await mountChat({history:async(conversation,before)=>{calls.push(before);if(!before)return {items:[row(5,'Recent question')],nextBeforeId:5};
  if(fail)throw new Error('offline');await new Promise(resolve=>{release=resolve});return {items:[row(4,'Older question')],nextBeforeId:4}}});
 try{document.querySelector('.chat-launcher').click();await flush();
  scrollChatToTop();scrollChatToTop();await flush();
  assert.deepEqual(calls,[undefined,5],'a scroll during loading does not start a second request');
  assert.match(document.querySelector('.chat-loading-older').textContent,/Đang tải trao đổi trước/);
  release();await flush();assert.equal(document.querySelector('.chat-loading-older'),null);
  fail=true;scrollChatToTop();await flush();assert.deepEqual(calls,[undefined,5,4]);
  scrollChatToTop();await flush();assert.equal(calls.length,3,'a failed page is not retried on every scroll');
  fail=false;[...document.querySelectorAll('button')].find(b=>b.textContent.includes('Tải lại trao đổi trước')).click();await flush();
  assert.deepEqual(calls,[undefined,5,4,4]);release();await flush();
  assert.equal([...document.querySelectorAll('button')].some(b=>b.textContent.includes('Tải lại trao đổi trước')),false);
 }finally{try{app.unmount()}catch{}}
});
test('a first page shorter than the panel keeps loading until the history ends',async()=>{
 const calls=[];
 const heights=Object.getOwnPropertyDescriptors(dom.window.HTMLElement.prototype);
 Object.defineProperty(dom.window.HTMLElement.prototype,'clientHeight',{configurable:true,get(){return this.classList?.contains('chat-body')?400:0}});
 Object.defineProperty(dom.window.HTMLElement.prototype,'scrollHeight',{configurable:true,get(){return this.classList?.contains('chat-body')?200:0}});
 const app=await mountChat({history:async(conversation,before)=>{calls.push(before);return before===undefined?{items:[row(3,'Q3')],nextBeforeId:3}:before===3?{items:[row(2,'Q2')],nextBeforeId:2}:{items:[row(1,'Q1')],nextBeforeId:null}}});
 try{document.querySelector('.chat-launcher').click();await flush();
  assert.deepEqual(calls,[undefined,3,2]);
 }finally{try{app.unmount()}catch{}
  for(const key of ['clientHeight','scrollHeight']){if(heights[key])Object.defineProperty(dom.window.HTMLElement.prototype,key,heights[key]);else delete dom.window.HTMLElement.prototype[key];}}
});
test('question inside a lesson sends the viewed course only as context',async()=>{
 const sent=[];
 const app=await mountChat({routeName:'LessonDetail',currentCourse:{id:'c1',name:'Cho vay'},history:async()=>({items:[],nextBeforeId:null}),chat:async(...args)=>{sent.push(args);return {answer:'A',grounded:true,sources:[],conversationId:'conv-1'}}});
 try{document.querySelector('.chat-launcher').click();await flush();
  assert.match(document.querySelector('.chat-context').textContent,/Cho vay/);
  const prompts=[...document.querySelectorAll('.quick-prompts button')].map(b=>b.textContent.trim());assert.ok(prompts.includes('Tóm tắt nội dung chính của khóa học'));
  [...document.querySelectorAll('.quick-prompts button')].find(b=>b.textContent.includes('Các bước')).click();await flush();
  assert.deepEqual(sent[0],['Các bước nghiệp vụ quan trọng là gì?','c1',undefined,undefined]);
 }finally{try{app.unmount()}catch{}}
});
