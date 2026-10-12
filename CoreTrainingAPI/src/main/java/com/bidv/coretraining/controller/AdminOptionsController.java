package com.bidv.coretraining.controller;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.*;
import com.bidv.coretraining.security.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/admin/options")
public class AdminOptionsController {
    private final AuthorizationService auth;private final OrganizationUnitRepository units;private final TrainingProgramRepository programs;private final CourseRepository courses;private final TrainingDomainRepository domains;
    @org.springframework.beans.factory.annotation.Autowired private UserGroupRepository groups;
    public AdminOptionsController(AuthorizationService auth,OrganizationUnitRepository units,TrainingProgramRepository programs,CourseRepository courses,TrainingDomainRepository domains){this.auth=auth;this.units=units;this.programs=programs;this.courses=courses;this.domains=domains;}
    @GetMapping("/{page}") public Map<String,Object> options(@PathVariable String page){
        AccessPolicy.require(Set.of("users","groups","roles","catalog","courses","exams","documents").contains(page));auth.requireOperation(page,"read");var u=auth.current();
        String permission=page+":read";
        return Map.of("canManageDomains",AccessPolicy.has(u,"catalog:create"),"units",units.findAll().stream().filter(n->AccessPolicy.system(u)||AccessPolicy.inScope(u,n)).map(n->new AccessManagementService.UnitDto(n.getId(),n.getName(),n.getType(),n.getParent()==null?null:n.getParent().getId(),n.isActive())).toList(),
            "groups",groups.findAll().stream().filter(g->AccessPolicy.allows(u,permission,g.getOrganizationUnit())).map(g->new AccessManagementService.GroupDto(g.getId(),g.getName(),g.getOrganizationUnit()==null?null:g.getOrganizationUnit().getId(),g.getMandatoryCourses().stream().map(com.bidv.coretraining.entity.Course::getId).collect(java.util.stream.Collectors.toSet()))).toList(),
            "courses",courses.findAll().stream().filter(c->AccessPolicy.allowsCourse(u,permission,c)).map(c->java.util.Map.of("id",c.getId(),"name",c.getName())).toList(),
            "domains",domains.findAll().stream().map(d->new TrainingStructureService.DomainDto(d.getId(),d.getCode(),d.getName(),d.getDescription(),d.getStatus())).toList(),
            "programs",programs.findAll().stream().filter(p->AccessPolicy.allowsProgram(u,permission,p)).map(p->new TrainingStructureService.ProgramDto(p.getId(),p.getDomain().getId(),p.getCode(),p.getName(),p.getDescription(),p.getStatus(),p.getUnits().stream().map(com.bidv.coretraining.entity.OrganizationUnit::getId).collect(java.util.stream.Collectors.toSet()),java.util.stream.Stream.of("read","create","update","delete").filter(action->AccessPolicy.allowsProgram(u,page+":"+action,p)).collect(java.util.stream.Collectors.toSet()))).toList());
    }
}
