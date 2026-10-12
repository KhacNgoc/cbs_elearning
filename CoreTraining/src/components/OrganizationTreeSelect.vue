<template>
  <div ref="container" class="unit-tree-select">
    <div ref="control" class="tree-select-control" :class="{open,disabled}" @click="show">
      <input ref="input" :value="open?query:displayLabel" :placeholder="open?displayLabel:'Chọn cấp trực thuộc'" :readonly="!open" :disabled="disabled" role="combobox" aria-haspopup="tree" aria-autocomplete="list" :aria-expanded="open" :aria-controls="treeId" :aria-label="label" @focus="onFocus" @input="query=($event.target as HTMLInputElement).value" @keydown="inputKeydown" />
      <button v-if="value && !disabled" type="button" tabindex="-1" class="clear-choice" :aria-label="`Xóa lựa chọn ${label.toLocaleLowerCase('vi')}`" @mousedown.prevent @click.stop="select('')"><CircleX :size="15" /></button>
      <button type="button" tabindex="-1" class="select-arrow" :aria-label="`${open?'Đóng':'Mở'} cây đơn vị`" :disabled="disabled" @mousedown.prevent @click.stop="open?close():show()"><ChevronDown :size="16" :class="{opened:open}" /></button>
    </div>
    <small v-if="selectedPath" class="selected-path">{{ selectedPath }}</small>
    <Teleport to="body"><div v-if="open" ref="popup" class="tree-select-popup" :style="popupStyle" @keydown.esc.stop.prevent="close()" @keydown.tab.prevent="popupTab($event.shiftKey)">
      <div :id="treeId" class="tree-options" role="tree" :aria-label="label" :style="{maxHeight:`${popupHeight}px`}">
        <div v-for="(row,index) in rows" :key="row.id" class="tree-option" role="treeitem" :aria-level="row.depth+1" :aria-selected="value===row.id" :aria-expanded="row.hasChildren?expanded.has(row.id):undefined" :tabindex="index===focusedIndex?0:-1" :style="{paddingLeft:`${.55+row.depth*1.25}rem`}" @click="select(row.id)" @keydown="rowKeydown($event,index)">
          <button v-if="row.hasChildren" type="button" tabindex="-1" class="option-toggle" :aria-label="`${expanded.has(row.id)?'Thu gọn':'Mở rộng'} ${row.name}`" @mousedown.prevent @click.stop="toggleBranch(row.id)"><ChevronRight :size="15" :class="{expanded:expanded.has(row.id)}" /></button><span v-else class="option-leaf"><CornerDownRight v-if="!row.id" :size="14" /></span>
          <span class="option-name"><template v-for="(part,partIndex) in highlight(row.name)" :key="partIndex"><mark v-if="part.match">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template><small v-if="!row.active">Ngừng hoạt động</small></span><Check v-if="value===row.id" :size="15" class="selected-check" />
        </div>
        <p v-if="rows.length===1 && query" class="no-matches">Không tìm thấy đơn vị phù hợp.</p>
      </div>
    </div></Teleport>
  </div>
</template>
<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,onMounted,ref,watch,type CSSProperties} from 'vue';
import {Check,ChevronDown,ChevronRight,CircleX,CornerDownRight} from 'lucide-vue-next';
import type {OrganizationUnit} from '../types/organization';
import {buildUnitForest,filterUnitForest,flattenUnitForest} from '../utils/organizationTree';
const props=withDefaults(defineProps<{modelValue?:string|null;units:readonly OrganizationUnit[];excludeIds?:ReadonlySet<string>;label?:string;disabled?:boolean}>(),{label:'Trực thuộc',disabled:false});
const emit=defineEmits<{'update:modelValue':[id:string]}>();
const container=ref<HTMLElement|null>(null),control=ref<HTMLElement|null>(null),input=ref<HTMLInputElement|null>(null),popup=ref<HTMLElement|null>(null);
const open=ref(false),query=ref(''),expanded=ref(new Set<string>()),focusedIndex=ref(0),popupHeight=ref(256),popupStyle=ref<CSSProperties>({});
let restoringFocus=false;
const treeId=`unit-parent-tree-${Math.random().toString(36).slice(2)}`;
const value=computed(()=>props.modelValue || '');
const selected=computed(()=>props.units.find(u=>u.id===value.value));
const displayLabel=computed(()=>selected.value?.name || value.value || 'Cấp gốc');
const forest=computed(()=>buildUnitForest(props.units.filter(u=>!props.excludeIds?.has(u.id))));
const allRows=computed(()=>flattenUnitForest(forest.value));
const selectedPath=computed(()=>{const row=allRows.value.find(r=>r.unit.id===value.value);return row && row.ancestors.length?[...row.ancestors.map(id=>props.units.find(u=>u.id===id)?.name || id),row.unit.name].join(' / '):'';});
const matchesQuery=(text:string)=>text.toLocaleLowerCase('vi').includes(query.value.trim().toLocaleLowerCase('vi'));
const filtered=computed(()=>query.value.trim()?filterUnitForest(forest.value,u=>matchesQuery(`${u.name} ${u.id}`)):forest.value);
const rows=computed(()=>[{id:'',name:'Cấp gốc',active:true,depth:0,hasChildren:false,ancestors:[] as string[]},...flattenUnitForest(filtered.value,expanded.value).map(row=>({id:row.unit.id,name:row.unit.name,active:row.unit.active,depth:row.depth,hasChildren:row.hasChildren,ancestors:row.ancestors}))]);
watch(query,()=>{focusedIndex.value=0;if(query.value.trim())expanded.value=new Set(flattenUnitForest(filtered.value).filter(r=>r.hasChildren).map(r=>r.unit.id));void nextTick(positionPopup);});
watch(()=>props.disabled,disabled=>{if(disabled)close(false);});
function positionPopup(){if(!open.value || !control.value)return;const rect=control.value.getBoundingClientRect();const body=control.value.closest('.admin-modal-body')?.getBoundingClientRect();if(rect.bottom<0 || rect.top>window.innerHeight || body && (rect.bottom<body.top || rect.top>body.bottom)){close(false);return;}
  const below=window.innerHeight-rect.bottom-12,above=rect.top-12;
  const upwards=below<180 && above>below;popupHeight.value=Math.max(80,Math.min(256,(upwards?above:below)-8));
  const width=Math.min(rect.width,window.innerWidth-16);
  popupStyle.value={width:`${width}px`,left:`${Math.max(8,Math.min(rect.left,window.innerWidth-width-8))}px`,top:upwards?undefined:`${rect.bottom+4}px`,bottom:upwards?`${window.innerHeight-rect.top+4}px`:undefined};
}
async function show(){if(props.disabled || open.value)return;query.value='';expanded.value=new Set(forest.value.map(n=>n.unit.id));const row=allRows.value.find(r=>r.unit.id===value.value);for(const id of row?.ancestors || [])expanded.value.add(id);open.value=true;await nextTick();positionPopup();input.value?.focus();popup.value?.querySelector<HTMLElement>('[role=treeitem][aria-selected=true]')?.scrollIntoView({block:'nearest'});}
function onFocus(){if(!restoringFocus)void show();}
function close(focus=true){open.value=false;query.value='';if(focus){restoringFocus=true;input.value?.focus();void nextTick(()=>{restoringFocus=false;});}}
function select(id:string){if(props.disabled || props.excludeIds?.has(id))return;emit('update:modelValue',id);close();}
function toggleBranch(id:string){const next=new Set(expanded.value);next.has(id)?next.delete(id):next.add(id);expanded.value=next;}
async function focusRow(index:number){focusedIndex.value=Math.max(0,Math.min(index,rows.value.length-1));await nextTick();popup.value?.querySelectorAll<HTMLElement>('[role=treeitem]')[focusedIndex.value]?.focus();}
async function inputKeydown(event:KeyboardEvent){
  if(event.key==='Escape' && open.value){event.preventDefault();event.stopPropagation();close();}
  else if(event.key==='ArrowDown' || event.key==='ArrowUp'){event.preventDefault();await show();void focusRow(event.key==='ArrowUp'?rows.value.length-1:Math.max(0,rows.value.findIndex(r=>r.id===value.value)));}
  else if(event.key==='Enter'){event.preventDefault();if(!open.value)void show();else{const match=query.value.trim()?rows.value.find(r=>r.id && matchesQuery(`${r.name} ${r.id}`)):rows.value.find(r=>r.id===value.value);if(match)select(match.id);}}
  else if(event.key==='Tab')close(false);
}
function rowKeydown(event:KeyboardEvent,index:number){const row=rows.value[index];if(!row)return;
  if(event.key==='ArrowDown'){event.preventDefault();void focusRow(index+1);}
  else if(event.key==='ArrowUp'){event.preventDefault();if(index===0)input.value?.focus();else void focusRow(index-1);}
  else if(event.key==='Home'){event.preventDefault();void focusRow(0);}
  else if(event.key==='End'){event.preventDefault();void focusRow(rows.value.length-1);}
  else if(event.key==='ArrowRight' && row.hasChildren){event.preventDefault();if(!expanded.value.has(row.id))toggleBranch(row.id);else void focusRow(index+1);}
  else if(event.key==='ArrowLeft'){event.preventDefault();if(row.hasChildren && expanded.value.has(row.id))toggleBranch(row.id);else{const parent=row.ancestors.at(-1);const parentIndex=rows.value.findIndex(r=>r.id===parent);if(parentIndex>=0)void focusRow(parentIndex);}}
  else if(event.key==='Enter' || event.key===' '){event.preventDefault();select(row.id);}
}
function popupTab(backwards:boolean){const form=control.value?.closest('form');const elements=form?[...form.querySelectorAll<HTMLElement>('input:not([disabled]),button:not([disabled]),select:not([disabled]),textarea:not([disabled]),a[href],[tabindex="0"]')].filter(el=>el.getClientRects().length>0 && el.tabIndex>=0):[];const index=elements.findIndex(el=>el===input.value);if(backwards){close();return;}close(false);elements[(index+1)%elements.length]?.focus();}
function outside(event:MouseEvent){if(open.value && event.target instanceof Node && !container.value?.contains(event.target) && !popup.value?.contains(event.target))close(false);}
function highlight(name:string){const needle=query.value.trim();if(!needle)return [{text:name,match:false}];const index=name.toLocaleLowerCase('vi').indexOf(needle.toLocaleLowerCase('vi'));if(index<0)return [{text:name,match:false}];return [{text:name.slice(0,index),match:false},{text:name.slice(index,index+needle.length),match:true},{text:name.slice(index+needle.length),match:false}].filter(part=>part.text);}
onMounted(()=>{document.addEventListener('click',outside);window.addEventListener('resize',positionPopup);window.addEventListener('scroll',positionPopup,true);});
onBeforeUnmount(()=>{document.removeEventListener('click',outside);window.removeEventListener('resize',positionPopup);window.removeEventListener('scroll',positionPopup,true);});
</script>
<style scoped>
.unit-tree-select{min-width:0}.tree-select-control{display:flex;align-items:center;gap:.35rem;width:100%;min-height:40px;border:1px solid var(--border-color);border-radius:8px;background:white;box-sizing:border-box;transition:border-color .15s,box-shadow .15s;cursor:pointer}.tree-select-control:hover:not(.disabled){border-color:var(--primary-color)}.tree-select-control.open,.tree-select-control:focus-within{border-color:var(--primary-color);box-shadow:0 0 0 2px #005b411a}.tree-select-control input{flex:1;min-width:0;width:100%;border:0!important;outline:0;padding:.65rem .75rem!important;background:transparent!important;font-size:.875rem;font-weight:400;box-shadow:none!important;color:var(--text-main)}.tree-select-control input::placeholder{color:var(--text-muted);opacity:.8}.tree-select-control.disabled{background:#f5f5f5;cursor:not-allowed;opacity:.6}.select-arrow,.clear-choice{display:grid;place-items:center;flex-shrink:0;width:22px;height:26px;border:0;background:transparent;color:#8a9690;padding:0;cursor:pointer}.select-arrow{margin-right:.5rem}.select-arrow svg.opened{transform:rotate(180deg)}.clear-choice:hover{color:var(--primary-color)}.selected-path{display:block;font-weight:400;font-size:.75rem;margin-top:.4rem;color:var(--text-muted);overflow-wrap:anywhere}.tree-select-popup{position:fixed;z-index:1800;padding:4px;border:1px solid #edf0ee;border-radius:8px;background:white;box-shadow:0 6px 16px #00000014,0 3px 6px #0000001f;box-sizing:border-box}.tree-options{overflow-y:auto;overscroll-behavior:contain;padding:2px 0}.tree-option{position:relative;display:flex;align-items:center;gap:.3rem;min-height:32px;margin:2px 0;padding:.3rem .5rem;border-radius:4px;font-size:.8125rem;font-weight:400;color:var(--text-main);cursor:pointer;outline:0;box-sizing:border-box}.tree-option[aria-selected=true]{background:var(--primary-light);color:var(--primary-color);font-weight:600}.tree-option:hover,.tree-option:focus-visible{background:#f0f2f1}.tree-option:focus-visible{box-shadow:inset 0 0 0 1px var(--primary-color)}.option-toggle,.option-leaf{display:grid;place-items:center;flex:0 0 20px;width:20px;height:24px;border:0;background:transparent;color:#7b8982;padding:0;cursor:pointer}.option-toggle svg.expanded{transform:rotate(90deg)}.option-name{flex:1;min-width:0;overflow-wrap:anywhere}.option-name small{display:block;margin-top:.15rem;font-size:.7rem;color:var(--text-muted);font-weight:400}.option-name mark{background:transparent;color:var(--primary-color);font-weight:700}.selected-check{flex-shrink:0;color:var(--primary-color)}.no-matches{margin:.75rem;color:var(--text-muted);font-size:.8rem}
</style>
