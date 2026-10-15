import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { scrollWithin } from '../src/utils/scrollWithin.ts';

function setup(overflow='auto') {
  const dom=new JSDOM(`<div id="hidden" style="overflow-y:hidden"><div id="viewport" style="overflow-y:${overflow}"><div id="target" style="scroll-margin-top:15px"></div></div></div>`);
  const {window}=dom;window.matchMedia=()=>({matches:false});
  const hidden=window.document.querySelector('#hidden'),viewport=window.document.querySelector('#viewport'),target=window.document.querySelector('#target');
  viewport.getBoundingClientRect=()=>({top:100,bottom:400});target.getBoundingClientRect=()=>({top:600,bottom:650});
  Object.defineProperty(viewport,'clientHeight',{value:300});
  const calls=[];viewport.scrollTo=options=>calls.push(options);hidden.scrollTo=()=>assert.fail('Hidden layout must never scroll');window.scrollBy=()=>assert.fail('Window must not scroll when a local viewport exists');
  return {window,hidden,viewport,target,calls};
}
test('only the nearest viewport scrolls; hidden layout ancestors stay unchanged',()=>{
  const {target,calls,hidden}=setup();scrollWithin(target);
  assert.deepEqual(calls,[{top:485,behavior:'smooth'}]);assert.equal(hidden.scrollTop,0);
});
test('nearest leaves a fully visible target unchanged',()=>{
  const {target,calls}=setup();target.getBoundingClientRect=()=>({top:150,bottom:200});
  scrollWithin(target,'nearest');assert.deepEqual(calls,[]);
});
test('a modal or popup viewport wins over the outer page viewport',()=>{
  const {hidden,target,calls}=setup();hidden.style.overflowY='auto';
  scrollWithin(target,'nearest',false);assert.deepEqual(calls,[{top:250,behavior:'auto'}]);
});
test('nearest scrolls only enough to reveal a clipped target',()=>{
  const {target,calls}=setup();scrollWithin(target,'nearest',false);
  assert.deepEqual(calls,[{top:250,behavior:'auto'}]);
});
test('reduced motion disables smooth scrolling',()=>{
  const {window,target,calls}=setup();window.matchMedia=()=>({matches:true});scrollWithin(target);
  assert.equal(calls[0].behavior,'auto');
});
test('document scrolling is used only without a local viewport',()=>{
  const {window,target}=setup('visible');const calls=[];window.scrollBy=options=>calls.push(options);
  scrollWithin(target,'start',false);assert.deepEqual(calls,[{top:585,behavior:'auto'}]);
});
test('missing targets are safe during route and conditional content transitions',()=>{
  assert.doesNotThrow(()=>scrollWithin(null));assert.doesNotThrow(()=>scrollWithin(undefined));
});
