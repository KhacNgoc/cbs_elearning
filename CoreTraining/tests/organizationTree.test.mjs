import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import ts from 'typescript';
const source=await readFile(new URL('../src/utils/organizationTree.ts',import.meta.url),'utf8');
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const {buildUnitForest,filterUnitForest,flattenUnitForest,unitDescendants}=await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const unit=(id,name,parentId=null,active=true)=>({id,name,parentId,active,type:'UNIT'});
const sample=[unit('LEAF','Tổ triển khai','TEAM'),unit('OTHER','Ban khác'),unit('TEAM','Nhóm phát triển','DEPT'),unit('ROOT','Ban Core Banking'),unit('DEPT','Phòng công nghệ','ROOT')];

test('four levels remain in parent order even when API returns children first',()=>{
  const rows=flattenUnitForest(buildUnitForest(sample));
  const leaf=rows.find(r=>r.unit.id==='LEAF');
  assert.equal(leaf.depth,3);assert.deepEqual(leaf.ancestors,['ROOT','DEPT','TEAM']);
  assert.deepEqual(rows.filter(r=>r.unit.id!=='OTHER').map(r=>r.unit.id),['ROOT','DEPT','TEAM','LEAF']);
});
test('each level expands independently and collapsing a parent hides its descendants',()=>{
  const forest=buildUnitForest(sample);
  assert.equal(flattenUnitForest(forest,new Set()).length,2);
  const root=flattenUnitForest(forest,new Set(['ROOT']));
  assert(root.some(r=>r.unit.id==='DEPT'));assert(!root.some(r=>r.unit.id==='TEAM'));
  const deep=flattenUnitForest(forest,new Set(['ROOT','DEPT','TEAM']));assert.equal(deep.length,5);
});
test('search for a leaf retains the full parent path without unrelated branches',()=>{
  const result=filterUnitForest(buildUnitForest(sample),u=>u.id==='LEAF');
  assert.deepEqual(flattenUnitForest(result).map(r=>r.unit.id),['ROOT','DEPT','TEAM','LEAF']);
});
test('status filtering preserves an inactive parent of an active result',()=>{
  const forest=buildUnitForest([unit('A','Ban',null,false),unit('B','Phòng','A',true)]);
  assert.deepEqual(flattenUnitForest(filterUnitForest(forest,u=>u.active)).map(r=>r.unit.id),['A','B']);
});
test('valid parent choices exclude self and descendants at every depth',()=>{
  assert.deepEqual([...unitDescendants(sample,'DEPT')].sort(),['DEPT','LEAF','TEAM']);
  const excluded=unitDescendants(sample,'DEPT');
  assert.deepEqual(flattenUnitForest(buildUnitForest(sample.filter(u=>!excluded.has(u.id)))).map(r=>r.unit.id).sort(),['OTHER','ROOT']);
});
test('a missing parent outside the permitted scope does not hide accessible units',()=>{
  const rows=flattenUnitForest(buildUnitForest([unit('B','Phòng','OUTSIDE'),unit('C','Tổ','B')]));
  assert.equal(rows[0].unit.id,'B');assert.equal(rows[1].depth,1);
});
test('invalid legacy cycles remain finite and retain each unit once',()=>{
  const data=[unit('A','A','B'),unit('B','B','A'),unit('C','C','C')];
  const rows=flattenUnitForest(buildUnitForest(data));
  assert.equal(rows.length,3);assert.equal(new Set(rows.map(r=>r.unit.id)).size,3);
  assert.deepEqual([...unitDescendants(data,'A')].sort(),['A','B']);
});
test('root pagination keeps a complete subtree on the same page',()=>{
  const data=Array.from({length:11},(_,i)=>[unit(`R${i}`,`Ban ${i}`),unit(`C${i}`,`Phòng ${i}`,`R${i}`)]).flat();
  const forest=buildUnitForest(data);
  assert.equal(flattenUnitForest(forest.slice(0,10)).length,20);
  assert.deepEqual(flattenUnitForest(forest.slice(10)).map(r=>r.unit.id),['R10','C10']);
});
test('siblings use natural Vietnamese name ordering',()=>{
  const rows=flattenUnitForest(buildUnitForest([unit('P10','Phòng 10'),unit('P2','Phòng 2')]));
  assert.deepEqual(rows.map(r=>r.unit.id),['P2','P10']);
});
