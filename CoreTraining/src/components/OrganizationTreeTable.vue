<template>
  <div class="admin-table-scroll">
    <table class="admin-data-table unit-tree-table" role="treegrid" aria-label="Cơ cấu đơn vị và phòng">
      <colgroup><col style="width:43%" /><col style="width:22%" /><col style="width:13%" /><col style="width:22%" /></colgroup>
      <thead><tr><th>Đơn vị / phòng</th><th>Trực thuộc</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>
        <tr v-for="row in rows" :key="row.unit.id" :aria-level="row.depth+1" :aria-expanded="row.hasChildren ? expandedIds.has(row.unit.id) : undefined" :class="{'unit-root':row.depth===0,'unit-context':isContext?.(row.unit)}">
          <td>
            <div class="unit-branch" :style="{'--unit-depth':row.depth}">
              <span v-if="row.depth" class="branch-elbow" aria-hidden="true"></span>
              <button v-if="row.hasChildren" type="button" class="unit-toggle" :aria-expanded="expandedIds.has(row.unit.id)" :aria-label="`${expandedIds.has(row.unit.id)?'Thu gọn':'Mở rộng'} ${row.unit.name}`" @click="$emit('toggle',row.unit.id)"><ChevronRight :size="18" aria-hidden="true" /></button>
              <span v-else class="unit-leaf" aria-hidden="true"><Building2 :size="17" /></span>
              <div class="unit-title"><strong>{{ row.unit.name }}</strong><small>{{ row.unit.id }}<span v-if="isContext?.(row.unit)"> · Cấp cha của kết quả</span></small></div>
            </div>
          </td>
          <td>{{ row.unit.parentId?parentName(row.unit.parentId):'Cấp gốc' }}</td>
          <td><span class="admin-status" :class="row.unit.active?'active':'inactive'">{{ row.unit.active?'Hoạt động':'Ngừng hoạt động' }}</span></td>
          <td><div v-if="canEdit || canCreate" class="admin-row-actions"><button v-if="canEdit && row.unit.allowedActions?.includes('update')" class="admin-row-edit" :disabled="busy" @click="$emit('edit',row.unit)">Sửa</button><button v-if="canCreate && row.unit.allowedActions?.includes('create')" class="admin-row-edit" :disabled="busy" :aria-label="`Thêm đơn vị con của ${row.unit.name}`" @click="$emit('addChild',row.unit)"><Plus :size="14" /> Thêm cấp con</button></div><span v-else>Chỉ xem</span></td>
        </tr>
        <tr v-if="!rows.length"><td colspan="4" class="admin-empty">Không có đơn vị phù hợp.</td></tr>
      </tbody>
    </table>
  </div>
</template>
<script setup lang="ts">
import {Building2,ChevronRight,Plus} from 'lucide-vue-next';
import type {OrganizationUnit,UnitTreeRow} from '../types/organization';
defineProps<{rows:UnitTreeRow[];expandedIds:ReadonlySet<string>;parentName:(id:string)=>string;canEdit:boolean;canCreate?:boolean;busy:boolean;isContext?:(unit:OrganizationUnit)=>boolean}>();
defineEmits<{toggle:[id:string];edit:[unit:OrganizationUnit];addChild:[unit:OrganizationUnit]}>();
</script>
<style scoped>
.unit-tree-table{min-width:760px}.unit-tree-table th:first-child{min-width:310px}.unit-branch{position:relative;display:flex;align-items:center;gap:.65rem;padding-left:calc(var(--unit-depth) * 26px);min-width:260px}.unit-title{min-width:0}.unit-title strong{font-weight:500}.unit-root .unit-title strong{font-weight:700;color:var(--primary-ink)}.unit-root{background:var(--surface-f5faf7)}.unit-tree-table tbody tr:hover{background:var(--surface-f0f6f3)}.unit-context .unit-title{color:var(--text-muted)}.unit-toggle{display:grid;place-items:center;flex:0 0 32px;width:32px;height:32px;border:1px solid var(--line-b8d5c8);border-radius:8px;background:var(--surface-edf6f1);color:var(--primary-ink);cursor:pointer}.unit-toggle svg{transition:transform .15s}.unit-toggle[aria-expanded=true] svg{transform:rotate(90deg)}.unit-leaf{display:grid;place-items:center;flex:0 0 32px;width:32px;height:32px;color:var(--ink-789c8b)}.branch-elbow{position:absolute;left:calc(var(--unit-depth) * 26px - 14px);top:0;width:13px;height:50%;border-left:1px solid var(--line-b8d5c8);border-bottom:1px solid var(--line-b8d5c8);border-bottom-left-radius:4px}.admin-row-actions{gap:.4rem}.admin-row-edit{display:inline-flex;align-items:center;gap:.3rem;white-space:nowrap}@media(prefers-reduced-motion:reduce){.unit-toggle svg{transition:none}}
</style>
