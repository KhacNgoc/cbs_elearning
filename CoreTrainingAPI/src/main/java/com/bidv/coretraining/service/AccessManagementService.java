package com.bidv.coretraining.service;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @Transactional
public class AccessManagementService {
    public record UnitDto(String id,String name,String type,String parentId,boolean active,Set<String> allowedActions) {
        public UnitDto(String id,String name,String type,String parentId,boolean active){this(id,name,type,parentId,active,Set.of());}
    }
    public record GroupDto(String id,String name,String unitId,Set<String> mandatoryCourseIds,Set<String> allowedActions) {
        public GroupDto(String id,String name,String unitId,Set<String> courses){this(id,name,unitId,courses,Set.of());}
    }
    public record CourseAccessDto(String ownerUnitId,Map<String,String> unitGrants,Set<Long> instructorIds) {}
    @org.springframework.beans.factory.annotation.Autowired private PermissionCache cache;
    private final AuthorizationService auth;
    private final OrganizationUnitRepository units;
    private final UserGroupRepository groups;
    private final UserRepository users;
    private final CourseRepository courses;
    public AccessManagementService(AuthorizationService auth,OrganizationUnitRepository units,UserGroupRepository groups,UserRepository users,CourseRepository courses) {this.auth=auth;this.units=units;this.groups=groups;this.users=users;this.courses=courses;}
    private Set<String> actions(String page,OrganizationUnit n){return java.util.stream.Stream.of("create","read","update","delete").filter(a->AccessPolicy.allows(auth.current(),page+":"+a,n)).collect(java.util.stream.Collectors.toSet());}
    public List<UnitDto> units() {auth.requireOperation("units","read");return units.findAll().stream().filter(auth::allowedScope).map(this::dto).toList();}
    private UnitDto dto(OrganizationUnit u) {return new UnitDto(u.getId(),u.getName(),u.getType(),u.getParent()==null?null:u.getParent().getId(),u.isActive(),actions("units",u));}
    public UnitDto saveUnit(String id,UnitDto r) {
        auth.permission("ORG_MANAGE");
        if(r.id()==null || r.id().isBlank() || r.name()==null || r.name().isBlank() || !Set.of("UNIT","DEPARTMENT").contains(r.type())) throw new BadRequestException("Mã, tên và loại đơn vị không hợp lệ");
        OrganizationUnit u=id==null?OrganizationUnit.builder().id(r.id().trim()).build():auth.unit(id);
        if(id==null && units.existsById(u.getId())) throw new BadRequestException("Mã đơn vị đã tồn tại");
        OrganizationUnit parent=r.parentId()==null || r.parentId().isBlank()?null:auth.unit(r.parentId());
        if(AccessPolicy.ancestors(parent).contains(u.getId())) throw new BadRequestException("Cơ cấu đơn vị không được tạo vòng lặp");
        if(id!=null)auth.scope(u);
        if(!AccessPolicy.system(auth.current())) {auth.scope(parent);AccessPolicy.require(id==null || Objects.equals(u.getParent()==null?null:u.getParent().getId(),r.parentId()));}
        u.setParent(parent);u.setName(r.name().trim());u.setType(r.type());u.setActive(r.active());cache.invalidateAfterCommit();return dto(units.save(u));
    }
    public List<GroupDto> groups() {auth.permission("GROUP_MANAGE");return groups.findAll().stream().filter(g->auth.allowedScope(g.getOrganizationUnit())).map(this::dto).toList();}
    private GroupDto dto(UserGroup g) {return new GroupDto(g.getId(),g.getName(),g.getOrganizationUnit()==null?null:g.getOrganizationUnit().getId(),g.getMandatoryCourses().stream().map(Course::getId).collect(java.util.stream.Collectors.toSet()),actions("groups",g.getOrganizationUnit()));}
    public GroupDto saveGroup(String id,GroupDto r) {
        auth.permission("GROUP_MANAGE");
        if(r.id()==null || r.id().isBlank() || r.name()==null || r.name().isBlank()) throw new BadRequestException("Mã và tên nhóm không được để trống");
        UserGroup g=id==null?UserGroup.builder().id(r.id().trim()).build():groups.findById(id).orElseThrow(()->new ResourceNotFoundException("Group"));
        if(id==null && groups.existsById(g.getId())) throw new BadRequestException("Mã nhóm đã tồn tại");
        if(id!=null) auth.scope(g.getOrganizationUnit());
        OrganizationUnit unit=r.unitId()==null?null:auth.unit(r.unitId());auth.scope(unit);
        Set<Course> mandatory=new HashSet<>();
        if(r.mandatoryCourseIds()!=null) for(String courseId:r.mandatoryCourseIds()) {
            Course c=courses.findById(courseId).orElseThrow(()->new ResourceNotFoundException("Course"));
            User probe=User.builder().role("ROLE_USER").organizationUnit(unit).build();
            AccessPolicy.require(AccessPolicy.visible(probe,c));mandatory.add(c);
        }
        // Moving an existing group cannot silently move its members into a foreign scope.
        if(id!=null && !Objects.equals(g.getOrganizationUnit()==null?null:g.getOrganizationUnit().getId(),r.unitId()) && users.findAll().stream().anyMatch(u->u.getUserGroup()!=null && u.getUserGroup().getId().equals(id))) throw new BadRequestException("Nhóm đang có thành viên; hãy chuyển thành viên trước khi đổi đơn vị");
        g.setOrganizationUnit(unit);g.setName(r.name().trim());g.setMandatoryCourses(mandatory);return dto(groups.save(g));
    }
    public void deleteGroup(String id) {auth.permission("GROUP_MANAGE");UserGroup g=groups.findById(id).orElseThrow(()->new ResourceNotFoundException("Group"));auth.scope(g.getOrganizationUnit());if(users.findAll().stream().anyMatch(u->u.getUserGroup()!=null && u.getUserGroup().getId().equals(id))) throw new BadRequestException("Nhóm đang có thành viên");groups.delete(g);}
    public CourseAccessDto courseAccess(String id) {Course c=courses.findById(id).orElseThrow(()->new ResourceNotFoundException("Course"));auth.manage(c);return new CourseAccessDto(c.getOwnerUnit()==null?null:c.getOwnerUnit().getId(),Map.copyOf(c.getUnitGrants()),Set.copyOf(c.getInstructorIds()));}
    public CourseAccessDto saveCourseAccess(String id,CourseAccessDto r) {
        Course c=courses.findById(id).orElseThrow(()->new ResourceNotFoundException("Course"));auth.manage(c);
        OrganizationUnit owner=r.ownerUnitId()==null || r.ownerUnitId().isBlank()?null:auth.unit(r.ownerUnitId());auth.scope(owner);
        Map<String,String> grants=r.unitGrants()==null?Map.of():r.unitGrants();
        for(var grant:grants.entrySet()) {
            OrganizationUnit unit=auth.unit(grant.getKey());
            if(!AccessPolicy.inScope(auth.current(),unit)) AccessPolicy.require(Objects.equals(c.getUnitGrants().get(grant.getKey()),grant.getValue()));
            if(!unit.isActive() || !Set.of("OPTIONAL","MANDATORY").contains(grant.getValue())) throw new BadRequestException("Quyền chia sẻ khóa học không hợp lệ");
        }
        for(var old:c.getUnitGrants().entrySet()) if(!AccessPolicy.inScope(auth.current(),auth.unit(old.getKey()))) AccessPolicy.require(Objects.equals(grants.get(old.getKey()),old.getValue()));
        Set<Long> instructors=r.instructorIds()==null?Set.of():r.instructorIds();
        for(Long userId:instructors) {User instructor=users.findById(userId).orElseThrow(()->new ResourceNotFoundException("User"));if(!AccessPolicy.inScope(auth.current(),instructor.getOrganizationUnit())) AccessPolicy.require(c.getInstructorIds().contains(userId));if(!AccessPolicy.has(instructor,"courses:update") || !instructor.activeAccount()) throw new BadRequestException("Người được phân công phải là giảng viên đang hoạt động");}
        for(Long old:c.getInstructorIds()) if(!AccessPolicy.inScope(auth.current(),users.findById(old).orElseThrow().getOrganizationUnit())) AccessPolicy.require(instructors.contains(old));
        c.setOwnerUnit(owner);c.setUnitGrants(new HashMap<>(grants));c.setInstructorIds(new HashSet<>(instructors));courses.save(c);return courseAccess(id);
    }
}
