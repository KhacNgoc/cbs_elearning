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
  [...document.querySelectorAll('button')].find(b=>b.textContent.includes('Tải trao đổi trước')).click();await flush();
  assert.deepEqual(calls[1],{conversation:'conversation',before:2});const text=document.querySelector('.chat-body').textContent;assert.ok(text.indexOf('Older question')<text.indexOf('Recent question'));
 }finally{try{app.unmount()}catch{}}
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
