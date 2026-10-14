<template>
  <main class="admin-data-page">
    <header class="admin-page-heading"><div><h1>{{ pageInfo.title }}</h1><p>{{ pageInfo.description }}</p></div><button class="btn btn-secondary" :disabled="busy || loading" @click="load"><RefreshCw :size="16" /> Tải lại</button></header>
    <p v-if="loading" class="admin-empty" role="status">Đang tải dữ liệu…</p>
    <template v-else>
      <section v-if="section==='users'" class="admin-data-section">
        <div class="admin-section-heading"><h2>{{ 'Danh sách tài khoản' }}</h2><div v-if="section==='users'" class="admin-row-actions"><template v-if="system"><button class="btn btn-secondary" :disabled="busy" @click="downloadTemplate"><Download :size="16" /> File mẫu</button><button class="btn btn-secondary" :disabled="busy" @click="excelInput?.click()"><Upload :size="16" /> Nhập Excel</button><input ref="excelInput" type="file" accept=".xlsx,.xls" hidden @change="importExcel" /></template><button v-if="user.can('users','create')" class="btn btn-primary" :disabled="busy" @click="editUser()"><Plus :size="16" /> Thêm tài khoản</button></div></div>
        <div class="admin-table-toolbar"><div class="admin-search-box"><Search :size="18" /><input v-model="query" type="search" placeholder="Tìm tên, tài khoản, email hoặc mã nhân viên…" aria-label="Tìm tài khoản" /></div><select v-model="unitFilter" aria-label="Lọc đơn vị"><option value="">Tất cả đơn vị</option><option v-for="u in units" :key="u.id" :value="u.id">{{ u.name }}</option></select><select v-if="section==='users'" v-model="statusFilter" aria-label="Lọc trạng thái tài khoản"><option value="">Tất cả trạng thái</option><option v-for="(label,key) in statusNames" :key="key" :value="key">{{ label }}</option></select></div>
        <div class="admin-table-scroll"><table class="admin-data-table account-table"><colgroup><col style="width:25%" /><col style="width:23%" /><col style="width:22%" /><col style="width:12%" /><col style="width:18%" /></colgroup><thead><tr><th>Người dùng</th><th>Đơn vị / nhóm</th><th>Vai trò</th><th>{{ section==='users'?'Trạng thái':'Phạm vi quản lý' }}</th><th>Thao tác</th></tr></thead><tbody><tr v-for="u in paginatedUsers" :key="u.id"><td><strong>{{ u.fullName }}</strong><small>{{ u.username }}</small><small v-if="section==='users'">{{ u.employeeId }} · {{ u.email }}</small></td><td>{{ unitName(u.unitId) }}<small v-if="u.userGroupId">{{ groups.find(g=>g.id===u.userGroupId)?.name || u.userGroupId }}</small></td><td>{{ (u.roles || []).map(code=>u.roleNames?.[code] || code).join(', ') || 'Chưa gán role' }}</td><td><span v-if="section==='users'" class="admin-status" :class="u.status==='ACTIVE' || !u.status?'active':'inactive'">{{ statusNames[u.status || 'ACTIVE'] }}</span></td><td><div class="admin-row-actions"><button v-if="u.allowedActions?.includes('update')" class="admin-row-edit" :disabled="busy" @click="editUser(u)">{{ section==='users'?'Sửa':'Phân quyền' }}</button><button v-if="section==='users' && user.can('roles')" class="admin-row-edit" @click="router.push({path:'/admin/roles',query:{user:String(u.id)}})"><ShieldCheck :size="14" /> Phân quyền</button></div></td></tr><tr v-if="!userTotal"><td colspan="5" class="admin-empty">Không có tài khoản phù hợp.</td></tr></tbody></table></div>
        <TablePagination v-model:page="userPage" v-model:page-size="userPageSize" :total="userTotal" :page-count="userPageCount" unit="tài khoản" unit-label="Tài khoản" />
      </section>
      <section v-if="section==='units'" class="admin-data-section">
        <div class="admin-section-heading"><h2>Danh sách đơn vị / phòng</h2><button v-if="user.can('units','create')" class="btn btn-primary" :disabled="busy" @click="editUnit()"><Plus :size="16" /> Thêm đơn vị / phòng</button></div>
        <div class="admin-table-toolbar"><div class="admin-search-box"><Search :size="18" /><input v-model="query" type="search" placeholder="Tìm mã hoặc tên đơn vị…" aria-label="Tìm đơn vị" /></div><select v-model="statusFilter" aria-label="Lọc trạng thái đơn vị"><option value="">Tất cả trạng thái</option><option value="true">Hoạt động</option><option value="false">Ngừng hoạt động</option></select></div>
        <div class="unit-tree-toolbar"><p>{{ units.length }} đơn vị / phòng<span v-if="unitFiltering"> · {{ unitMatchCount }} kết quả phù hợp (giữ cấp cha để thấy cơ cấu)</span></p><div class="admin-row-actions"><button type="button" class="admin-row-edit" @click="expandUnits(true)">Mở tất cả</button><button type="button" class="admin-row-edit" @click="expandUnits(false)">Thu gọn tất cả</button></div></div>
        <OrganizationTreeTable :rows="visibleUnitRows" :expanded-ids="effectiveUnitExpansion" :parent-name="unitName" :can-edit="user.can('units','update')" :can-create="user.can('units','create')" :busy="busy" :is-context="unit=>unitFiltering && !unitMatches(unit)" @toggle="toggleUnitBranch" @edit="editUnit" @add-child="addChildUnit" />
        <TablePagination v-model:page="unitPage" v-model:page-size="unitPageSize" :total="unitRootTotal" :page-count="unitPageCount" unit="nhánh gốc" unit-label="Nhánh gốc" />
        <p class="unit-tree-help">Phân trang theo nhánh gốc; các cấp con được giữ cùng nhánh. Nhấn mũi tên để mở hoặc thu gọn từng cấp.</p>
      </section>
      <section v-if="section==='groups'" class="admin-data-section">
        <div class="admin-section-heading"><h2>Danh sách nhóm học viên</h2><button v-if="user.can('groups','create')" class="btn btn-primary" :disabled="busy" @click="editGroup()"><Plus :size="16" /> Thêm nhóm</button></div>
        <div class="admin-table-toolbar"><div class="admin-search-box"><Search :size="18" /><input v-model="query" type="search" placeholder="Tìm mã hoặc tên nhóm…" aria-label="Tìm nhóm" /></div><select v-model="unitFilter" aria-label="Lọc đơn vị của nhóm"><option value="">Tất cả đơn vị</option><option v-for="u in units" :key="u.id" :value="u.id">{{ u.name }}</option></select></div>
        <div class="admin-table-scroll"><table class="admin-data-table"><colgroup><col style="width:30%" /><col style="width:25%" /><col style="width:25%" /><col style="width:20%" /></colgroup><thead><tr><th>Nhóm học viên</th><th>Đơn vị</th><th>Khóa bắt buộc</th><th>Thao tác</th></tr></thead><tbody><tr v-for="g in paginatedGroups" :key="g.id"><td><strong>{{ g.name }}</strong><small>{{ g.id }}</small></td><td>{{ unitName(g.unitId) }}</td><td>{{ g.mandatoryCourseIds.length }} khóa học</td><td><div class="admin-row-actions"><button v-if="g.allowedActions?.includes('update')" class="admin-row-edit" :disabled="busy" @click="editGroup(g)">Sửa</button><button v-if="g.allowedActions?.includes('delete')" class="admin-row-delete" :disabled="busy" @click="deleteTarget=g">Xóa</button></div></td></tr><tr v-if="!groupTotal"><td colspan="4" class="admin-empty">Không có nhóm phù hợp.</td></tr></tbody></table></div>
        <TablePagination v-model:page="groupPage" v-model:page-size="groupPageSize" :total="groupTotal" :page-count="groupPageCount" unit="nhóm" unit-label="Nhóm" />
      </section>
    </template>
    <AdminModal :open="!!editor" :title="modalTitle" :busy="busy" :width="720" @close="closeEditor" @submit="submitEditor">
      <template v-if="editor==='user'">
        <div class="admin-form-grid"><label class="admin-form-field">Tài khoản *<input v-model="userForm.username" :disabled="!!editingId" required /></label><label class="admin-form-field">Họ và tên *<input v-model="userForm.fullName" required /></label><label class="admin-form-field">Email<input v-model="userForm.email" type="email" /></label><label class="admin-form-field">Mã nhân viên<input v-model="userForm.employeeId" /></label></div>
        <label class="admin-form-field">{{ editingId?'Mật khẩu mới':'Mật khẩu ban đầu *' }}<input v-model="userForm.password" type="password" autocomplete="new-password" :required="!editingId" minlength="8" /><small>{{ editingId?'Để trống để giữ mật khẩu hiện tại.':'Tối thiểu 8 ký tự. Người dùng sẽ đổi mật khẩu ở lần đăng nhập đầu tiên.' }}</small></label>
        <div class="admin-form-grid"><label class="admin-form-field">Trạng thái<select v-model="userForm.status"><option v-for="(label,key) in statusNames" :key="key" :value="key">{{ label }}</option></select></label><label class="admin-form-field">Đối tượng đào tạo<select v-model="userForm.learnerType"><option v-for="(label,key) in learnerNames" :key="key" :value="key">{{ label }}</option></select></label></div>
        <div class="admin-form-grid"><label class="admin-form-field">Đơn vị công tác<select v-model="userForm.unitId"><option v-if="system" value="">Chưa gán đơn vị</option><option v-for="u in activeUnits" :key="u.id" :value="u.id">{{ u.name }}</option></select></label><label class="admin-form-field">Nhóm học viên<select v-model="userForm.userGroupId"><option value="">Chưa phân nhóm</option><option v-for="g in eligibleGroups" :key="g.id" :value="g.id">{{ g.name }}</option></select></label></div>
        <p class="admin-modal-help">Vai trò hiện tại: {{ userForm.roles.map(code=>users.find(u=>u.id===editingId)?.roleNames?.[code] || code).join(', ') || 'Chưa gán role' }}. Cấp hoặc thay đổi vai trò tại menu Quản lý role.</p>
        <LearnerDeadlineEditor v-if="editingId" :key="editingId" :user-id="editingId" :disabled="busy" />
      </template>
      <template v-else-if="editor==='unit'">
        <div class="admin-form-grid"><label class="admin-form-field">Mã đơn vị *<input v-model="unitForm.id" :disabled="!!editingId" required /></label><label class="admin-form-field">Tên đơn vị / phòng *<input v-model="unitForm.name" required /></label></div>
        <div class="admin-form-field"><span>Trực thuộc</span><OrganizationTreeSelect v-model="unitForm.parentId" :units="units" :exclude-ids="excludedParentIds" :disabled="busy" label="Trực thuộc" /><small v-if="editingId">Không thể chọn chính đơn vị này hoặc các đơn vị con làm cấp cha.</small></div>
        <ToggleSwitch v-model="unitForm.active" label="Đang hoạt động" :disabled="busy" />
      </template>
      <template v-else-if="editor==='group'">
        <div class="admin-form-grid"><label class="admin-form-field">Mã nhóm *<input v-model="groupForm.id" :disabled="!!editingId" required /></label><label class="admin-form-field">Tên nhóm *<input v-model="groupForm.name" required /></label></div>
        <label class="admin-form-field">Đơn vị *<select v-model="groupForm.unitId" required><option value="">Chọn đơn vị</option><option v-for="u in activeUnits" :key="u.id" :value="u.id">{{ u.name }}</option></select></label>
        <fieldset class="admin-choice-group"><legend>Khóa học bắt buộc</legend><div v-for="c in courses" :key="c.id" class="admin-choice-row"><ToggleSwitch :model-value="groupForm.mandatoryCourseIds.includes(c.id)" :label="c.name" :disabled="busy" @update:model-value="checked=>toggleSelection(groupForm.mandatoryCourseIds,c.id,checked)" /></div><p v-if="!courses.length">Chưa có khóa học để giao cho nhóm.</p></fieldset>
      </template>
    </AdminModal>
    <ConfirmModal :open="!!deleteTarget" title="Xóa nhóm học viên?" :message="`Xóa nhóm ${deleteTarget?.name || ''}? Nhóm có thành viên sẽ không thể xóa.`" :loading="busy" confirm-label="Xóa nhóm" @cancel="!busy && (deleteTarget=null)" @confirm="deleteGroup" />
  </main>
</template>
<script setup lang="ts">
import {computed,onMounted,ref,watch} from 'vue';
import {Search,Plus,RefreshCw,Download,Upload,ShieldCheck} from 'lucide-vue-next';
import AdminModal from '../components/AdminModal.vue';
import LearnerDeadlineEditor from '../components/LearnerDeadlineEditor.vue';
import ConfirmModal from '../components/ConfirmModal.vue';
import ToggleSwitch from '../components/ToggleSwitch.vue';
import TablePagination from '../components/TablePagination.vue';
import {useTablePagination} from '../composables/useTablePagination';
import {useRoute,useRouter} from 'vue-router';
import {axiosClient} from '../api/axiosClient';
import {showToast} from '../utils/toast';
import {adminApi,type AdminUser} from '../api/adminApi';
import type {Course} from '../types';
import {useUserStore} from '../stores/userStore';
import type {OrganizationUnit as Unit} from '../types/organization';
import OrganizationTreeTable from '../components/OrganizationTreeTable.vue';
import OrganizationTreeSelect from '../components/OrganizationTreeSelect.vue';
import {buildUnitForest,filterUnitForest,flattenUnitForest,unitDescendants} from '../utils/organizationTree';
interface Group {id:string;name:string;unitId:string;mandatoryCourseIds:string[];allowedActions?:string[]}
const route=useRoute(),router=useRouter(),user=useUserStore();
const units=ref<Unit[]>([]),groups=ref<Group[]>([]),users=ref<AdminUser[]>([]),courses=ref<Course[]>([]);
const loading=ref(false),busy=ref(false),query=ref('');
const section=computed(()=>String(route.meta.accessSection || 'users'));
const pages:Record<string,{title:string;description:string}>={
 users:{title:'Tài khoản',description:'Quản lý hồ sơ, trạng thái và đơn vị công tác của người dùng.'},
 units:{title:'Đơn vị / phòng',description:'Tổ chức cơ cấu đơn vị, phòng và các cấp trực thuộc.'},
 groups:{title:'Nhóm học viên',description:'Quản lý nhóm học viên và các khóa học bắt buộc.'},
 permissions:{title:'Phân quyền',description:'Gán vai trò, phạm vi quản lý, giảng viên và quyền học khóa học.'}
};
const pageInfo=computed(()=>pages[section.value] || pages.permissions!);
const unitFilter=ref(''),statusFilter=ref('');
const system=computed(()=>user.systemAdmin);
const statusNames:Record<string,string>={ACTIVE:'Hoạt động',LOCKED:'Khóa',DISABLED:'Ngừng sử dụng'};
const learnerNames:Record<string,string>={NEW_STAFF:'Cán bộ mới',TRANSFERRED_STAFF:'Cán bộ luân chuyển',CURRENT_STAFF:'Cán bộ đang công tác',EXTERNAL:'Học viên đơn vị khác'};
const activeUnits=computed(()=>units.value.filter(u=>u.active));
const unitName=(id?:string|null)=>units.value.find(u=>u.id===id)?.name || id || 'Chưa gán';
const matches=(text:string,query:string)=>text.toLocaleLowerCase('vi').includes(query.trim().toLocaleLowerCase('vi'));
const filteredUsers=computed(()=>users.value.filter(u=>matches(`${u.username} ${u.fullName} ${u.employeeId || ''} ${u.email || ''}`,query.value) && (!unitFilter.value || u.unitId===unitFilter.value) && (!statusFilter.value || (u.status || 'ACTIVE')===statusFilter.value)));
const unitForest=computed(()=>buildUnitForest(units.value));
const unitFiltering=computed(()=>Boolean(query.value.trim() || statusFilter.value));
const unitMatches=(unit:Unit)=>matches(`${unit.id} ${unit.name}`,query.value) && (!statusFilter.value || String(unit.active)===statusFilter.value);
const filteredUnitForest=computed(()=>unitFiltering.value?filterUnitForest(unitForest.value,unitMatches):unitForest.value);
const expandedUnitIds=ref(new Set<string>()),filteredExpandedUnitIds=ref(new Set<string>());
const effectiveUnitExpansion=computed(()=>unitFiltering.value?filteredExpandedUnitIds.value:expandedUnitIds.value);
let initializedUnitTree=false;
watch(units,()=>{if(!initializedUnitTree && units.value.length){expandedUnitIds.value=new Set(unitForest.value.map(branch=>branch.unit.id));initializedUnitTree=true;}if(unitFiltering.value)filteredExpandedUnitIds.value=new Set(flattenUnitForest(filteredUnitForest.value).filter(row=>row.hasChildren).map(row=>row.unit.id));});
watch([query,statusFilter],()=>{if(unitFiltering.value)filteredExpandedUnitIds.value=new Set(flattenUnitForest(filteredUnitForest.value).filter(row=>row.hasChildren).map(row=>row.unit.id));});
function toggleUnitBranch(id:string){const target=unitFiltering.value?filteredExpandedUnitIds:expandedUnitIds;const next=new Set(target.value);next.has(id)?next.delete(id):next.add(id);target.value=next;}
function expandUnits(all:boolean){const target=unitFiltering.value?filteredExpandedUnitIds:expandedUnitIds;target.value=all?new Set(flattenUnitForest(filteredUnitForest.value).filter(row=>row.hasChildren).map(row=>row.unit.id)):new Set();}

const filteredGroups=computed(()=>groups.value.filter(g=>matches(`${g.id} ${g.name}`,query.value) && (!unitFilter.value || g.unitId===unitFilter.value)));
const {page:userPage,pageSize:userPageSize,total:userTotal,pageCount:userPageCount,paginated:paginatedUsers,reset:resetUsers}=useTablePagination(filteredUsers);
const {page:unitPage,pageSize:unitPageSize,total:unitRootTotal,pageCount:unitPageCount,paginated:paginatedUnitRoots,reset:resetUnits}=useTablePagination(filteredUnitForest);
const {page:groupPage,pageSize:groupPageSize,total:groupTotal,pageCount:groupPageCount,paginated:paginatedGroups,reset:resetGroups}=useTablePagination(filteredGroups);
const visibleUnitRows=computed(()=>flattenUnitForest(paginatedUnitRoots.value,effectiveUnitExpansion.value));
const unitMatchCount=computed(()=>units.value.filter(unitMatches).length);
watch([query,unitFilter,statusFilter],()=>{resetUsers();resetUnits();resetGroups();});
watch(section,()=>{query.value='';unitFilter.value='';statusFilter.value='';editor.value='';resetUsers();resetUnits();resetGroups();void load();});
function toggleSelection<T>(items:T[],item:T,enabled:boolean){const index=items.indexOf(item);if(enabled && index<0)items.push(item);else if(!enabled && index>=0)items.splice(index,1);}
const editor=ref<''|'user'|'unit'|'group'>(''),editingId=ref<string|number|null>(null);
const blankUser=()=>({username:'',fullName:'',password:'',email:'',employeeId:'',avatarColor:'#005B41',points:0,role:'UNASSIGNED',roles:[] as string[],status:'ACTIVE',learnerType:'CURRENT_STAFF',unitId:'',scopeUnitIds:[] as string[],userGroupId:''});
const userForm=ref(blankUser()),unitForm=ref<Unit>({id:'',name:'',type:'UNIT',parentId:'',active:true}),groupForm=ref<Group>({id:'',name:'',unitId:'',mandatoryCourseIds:[]});
const excludedParentIds=computed(()=>editor.value==='unit' && typeof editingId.value==='string'?unitDescendants(units.value,editingId.value):new Set<string>());
function addChildUnit(unit:Unit){expandedUnitIds.value=new Set([...expandedUnitIds.value,unit.id]);editUnit(undefined,unit.id);}
const eligibleGroups=computed(()=>{
 const ancestors=new Set<string>();let current=userForm.value.unitId;
 while(current && !ancestors.has(current)){ancestors.add(current);current=units.value.find(u=>u.id===current)?.parentId || '';}
 return groups.value.filter(g=>!g.unitId || ancestors.has(g.unitId));
});
const excelInput=ref<HTMLInputElement|null>(null);
function error(e:any){showToast(e.response?.data?.message || 'Không thực hiện được thao tác','error');}
async function load(){loading.value=true;try{
 await user.refreshAccess();
 if(section.value==='units'){units.value=(await axiosClient.get<Unit[]>('/api/admin/units')).data;return;}
 const options=(await axiosClient.get(`/api/admin/options/${section.value}`)).data;units.value=options.units;
 if(section.value==='users'){users.value=(await axiosClient.get<AdminUser[]>('/api/admin/users')).data;groups.value=options.groups || [];}
 if(section.value==='groups'){groups.value=(await axiosClient.get<Group[]>('/api/admin/access/groups')).data;courses.value=options.courses || [];}
 }catch(e){error(e);}finally{loading.value=false;}}
function closeEditor(){if(!busy.value){editor.value='';if(route.query.user)router.replace({query:{}});}}
function editUser(u?:AdminUser){editingId.value=u?.id || null;userForm.value={...blankUser(),...u,password:'',unitId:u?.unitId || '',userGroupId:u?.userGroupId || '',roles:[...(u?.roles || [])],scopeUnitIds:[...(u?.scopeUnitIds || [])],status:u?.status || 'ACTIVE',learnerType:u?.learnerType || 'CURRENT_STAFF'};editor.value='user';}
function editUnit(u?:Unit,parentId=''){editingId.value=u?.id || null;unitForm.value=u?{...u,parentId:u.parentId || ''}:{id:'',name:'',type:'UNIT',parentId,active:true};editor.value='unit';}
function editGroup(g?:Group){editingId.value=g?.id || null;groupForm.value=g?{...g,mandatoryCourseIds:[...g.mandatoryCourseIds]}:{id:'',name:'',unitId:'',mandatoryCourseIds:[]};editor.value='group';}
async function saveEditor(){busy.value=true;try{const endpoint=editor.value==='user'?'users':editor.value==='unit'?'units':'groups';const data=editor.value==='user'?userForm.value:editor.value==='unit'?unitForm.value:groupForm.value;await axiosClient.request({method:editingId.value?'put':'post',url:`/api/admin/${endpoint}${editingId.value?'/'+encodeURIComponent(String(editingId.value)):''}`,data});editor.value='';if(route.query.user)await router.replace({query:{}});showToast('Đã lưu','success');await load();}catch(e){error(e);}finally{busy.value=false;}}
const deleteTarget=ref<Group|null>(null);
async function deleteGroup(){if(!deleteTarget.value)return;busy.value=true;try{await axiosClient.delete(`/api/admin/groups/${encodeURIComponent(deleteTarget.value.id)}`);deleteTarget.value=null;showToast('Đã xóa nhóm','success');await load();}catch(e){error(e);}finally{busy.value=false;}}
const modalTitle=computed(()=>editor.value==='user'?(editingId.value?'Cập nhật tài khoản':'Thêm tài khoản'):editor.value==='unit'?(editingId.value?'Cập nhật đơn vị / phòng':'Thêm đơn vị / phòng'):(editingId.value?'Cập nhật nhóm học viên':'Thêm nhóm học viên'));
async function submitEditor(){await saveEditor();}
async function downloadTemplate(){busy.value=true;try{const blob=await adminApi.downloadUserTemplate();const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download='user_import_template.xlsx';link.click();URL.revokeObjectURL(url);}catch(e){error(e);}finally{busy.value=false;}}
async function importExcel(event:Event){const input=event.target as HTMLInputElement;const file=input.files?.[0];if(!file)return;busy.value=true;try{await adminApi.importUsersExcel(file);showToast('Đã nhập tài khoản. Gán đơn vị, nhóm và role tại menu Quản lý role.','success');await load();}catch(e){error(e);}finally{input.value='';busy.value=false;}}
onMounted(load);
</script>
<style scoped>
.unit-tree-toolbar{display:flex;align-items:center;justify-content:space-between;gap:1rem;margin-bottom:.85rem;flex-wrap:wrap}.unit-tree-toolbar p,.unit-tree-help{color:var(--text-muted);font-size:.8rem;line-height:1.5}.unit-tree-help{margin-top:.75rem}

.grant-field{margin:0;min-width:220px}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}.admin-row-edit{display:inline-flex;align-items:center;gap:.4rem}.admin-page-heading .btn,.admin-section-heading .btn{display:inline-flex;align-items:center;gap:.4rem}@media(max-width:900px){.admin-section-heading{flex-wrap:wrap}.admin-section-heading .admin-row-actions{flex-wrap:wrap}}
</style>
