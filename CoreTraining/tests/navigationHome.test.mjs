import test from 'node:test';
import assert from 'node:assert/strict';
import { navigationHome } from '../src/utils/navigationHome.ts';
const item=(id,route,area='ADMIN',visible=true)=>({id,route,area,visible,label:id,icon:'Home',sortOrder:0});
test('admin overview is home regardless of navigation order or legacy overview URL',()=>{
  assert.equal(navigationHome([item('admin.courses','/admin/courses'),item('admin.overview','/admin?tab=overview')]),'/admin');
});
test('restricted administrators use an allowed visible page',()=>{
  assert.equal(navigationHome([item('admin.overview','/admin','ADMIN',false),item('admin.courses','/admin/courses')]),'/admin/courses');
});
test('learners keep their learning home',()=>{
  assert.equal(navigationHome([item('dashboard','/','MAIN')]),'/');
  assert.equal(navigationHome([]),'/');
});
