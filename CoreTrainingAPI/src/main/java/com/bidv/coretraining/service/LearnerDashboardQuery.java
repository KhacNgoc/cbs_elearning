package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.CourseDto;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.security.AccessPolicy;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.text.Normalizer;
import java.time.*;
import java.util.*;

/** Metadata projections only. Authorization, progress state and filtering precede SQL page boundaries. */
@Repository
public class LearnerDashboardQuery {
    private final NamedParameterJdbcTemplate jdbc;
    public LearnerDashboardQuery(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Context(String sql, Map<String,Object> params) {}
    public record Filter(String tab,String search,String status,String domainId,String businessModule,String programId,
                         String level,String source,boolean mandatory,String sort,int page,int size) {
        public static Filter defaults() { return new Filter("mine","","","","","","","",false,"deadline",1,6); }
    }
    public record Card(CourseDto course,String source,boolean mandatory,String learningStatus,int completedCount,
                       int totalCount,int percent,LocalDate dueDate,LocalDateTime lastAccessedAt,
                       LearnerWorkspaceService.Resume resume) {}
    public record Page(List<Card> items,long total,int page,int size) {}
    public record Summary(long joined,long discover,long notStarted,long inProgress,long complete,long mandatory,
                          long mandatoryComplete,long completed,long total,Integer percent) {}
    private static String published(String alias) { return "("+alias+".status is null or upper("+alias+".status) in ('ACTIVE','PUBLISHED'))"; }
    private static String in(String expression, Collection<?> values,String name,Map<String,Object> p) {
        if(values.isEmpty()) return "1=0"; p.put(name,values);return expression+" in (:"+name+")";
    }
    /** The same unpaged visible relation is reusable for assessment histories (including archived courses). */
    public Context visible(User u) {
        Map<String,Object> p=new HashMap<>();p.put("uid",u.getId());
        record Unit(String id,String parent,boolean active) {}
        Map<String,Unit> units=new HashMap<>();
        jdbc.query("select id,parent_id,active from organization_units",Map.of(),r->{
            String id=r.getString(1);units.put(id,new Unit(id,r.getString(2),r.getBoolean(3)));
        });
        java.util.function.Function<String,Set<String>> ancestors=id->{
            Set<String> seen=new HashSet<>();String n=id;
            while(n!=null) {var unit=units.get(n);if(unit==null||!unit.active()||!seen.add(n))return Set.of();n=unit.parent();}
            return seen;
        };
        String actorUnit=u.getOrganizationUnit()==null?null:u.getOrganizationUnit().getId();
        Set<String> own=ancestors.apply(actorUnit);
        java.util.function.Function<Set<String>,Set<String>> descendants=scope->{
            Set<String> result=new HashSet<>();units.keySet().forEach(id->{if(ancestors.apply(id).stream().anyMatch(scope::contains))result.add(id);});return result;
        };
        String registered="exists(select 1 from user_registered_courses rc where rc.user_id=:uid and rc.course_id=c.id)";
        List<String> admin=new ArrayList<>(),editor=new ArrayList<>();
        if(u.activeAccount()&&(AccessPolicy.system(u)||actorUnit==null||!own.isEmpty())) {
            if(AccessPolicy.system(u)) admin.add("1=1");
            else if(u.isDynamicAccessInitialized()) {
                int i=0;
                for(var role:AccessPolicy.policies(u)) {
                    if(!role.active()||role.learnerRole()||(!role.permissions().contains("courses:read")&&!role.permissions().contains("courses:update"))) continue;
                    String rule;
                    if(role.assignedCoursesOnly()) rule="exists(select 1 from course_instructors ci where ci.course_id=c.id and ci.user_id=:uid)";
                    else {String ids="role"+(i++);var scope=descendants.apply(role.unitIds());
                        rule=role.legacyOwnerScope()?in("c.owner_unit_id",scope,ids,p):"exists(select 1 from training_program_units pu where pu.program_id=c.program_id and "+in("pu.unit_id",scope,ids,p)+")";}
                    admin.add(rule);if(role.permissions().contains("courses:read"))editor.add(rule);
                }
            } else {
                var scope=u.getScopeUnitIds().isEmpty()?(actorUnit==null?Set.<String>of():Set.of(actorUnit)):u.getScopeUnitIds();
                if(u.hasRole("ROLE_UNIT_ADMIN")) admin.add(in("c.owner_unit_id",descendants.apply(scope),"legacyScope",p));
                if(u.hasRole("ROLE_INSTRUCTOR")) {
                    var activeOwners=units.keySet().stream().filter(id->!ancestors.apply(id).isEmpty()).toList();
                    admin.add("((c.owner_unit_id is null or "+in("c.owner_unit_id",activeOwners,"activeOwners",p)+") and exists(select 1 from course_instructors ci where ci.course_id=c.id and ci.user_id=:uid))");
                }
            }
        }
        if(AccessPolicy.system(u)||!u.isDynamicAccessInitialized())editor.addAll(admin);
        String editorRule=editor.isEmpty()?"1=0":"("+String.join(" or ",editor)+")";
        List<String> allow=new ArrayList<>(admin);
        if(own.isEmpty()) { if(actorUnit==null)allow.add("c.owner_unit_id is null"); }
        else {
            allow.add(registered);
            for(String[] grant:List.of(new String[]{"course_unit_grants","course_id","c.id"},new String[]{"program_unit_grants","program_id","c.program_id"},new String[]{"domain_unit_grants","domain_id","p.domain_id"}))
                allow.add("exists(select 1 from "+grant[0]+" g where g."+grant[1]+"="+grant[2]+" and "+in("g.unit_id",own,"ownUnits",p)+")");
            allow.add(in("c.owner_unit_id",own,"ownUnits",p));
            allow.add("exists(select 1 from training_program_units pu where pu.program_id=c.program_id and "+in("pu.unit_id",own,"ownUnits",p)+")");
            allow.add("exists(select 1 from training_domain_units du where du.domain_id=p.domain_id and "+in("du.unit_id",own,"ownUnits",p)+")");
        }
        return new Context("with visible as (select c.*,p.name program_name,p.domain_id,d.name domain_name,case when "+editorRule+" then 1 else 0 end editor_access from courses c join training_programs p on p.id=c.program_id join training_domains d on d.id=p.domain_id where "+(allow.isEmpty()?"1=0":"("+String.join(" or ",allow)+")")+")",p);
    }
    public Context facts(User u) {
        var v=visible(u);Map<String,Object> p=new HashMap<>(v.params());
        Set<String> own=u.getOrganizationUnit()!=null&&AccessPolicy.activeUnit(u.getOrganizationUnit())?AccessPolicy.ancestors(u.getOrganizationUnit()):Set.of();
        String courseGrant=in("cg.unit_id",own,"grantUnits",p),programGrant=in("pg.unit_id",own,"grantUnits",p),domainGrant=in("dg.unit_id",own,"grantUnits",p);
        String mandatory="(case when exists(select 1 from course_unit_grants cg where cg.course_id=c.id and "+courseGrant+") then case when exists(select 1 from course_unit_grants cg where cg.course_id=c.id and "+courseGrant+" and upper(cg.grant_type)='MANDATORY') then 1 else 0 end when exists(select 1 from program_unit_grants pg where pg.program_id=c.program_id and "+programGrant+") then case when exists(select 1 from program_unit_grants pg where pg.program_id=c.program_id and "+programGrant+" and upper(pg.grant_type)='MANDATORY') then 1 else 0 end when exists(select 1 from domain_unit_grants dg where dg.domain_id=c.domain_id and "+domainGrant+" and upper(dg.grant_type)='MANDATORY') then 1 else 0 end=1 or exists(select 1 from user_group_memberships gm join group_mandatory_courses gc on gc.group_id=gm.group_id where gm.user_id=:uid and gc.course_id=c.id))";
        String sql=v.sql()+"""
        , lessons_visible as (
          select l.id,l.course_id,l.chapter_id,
            case when exists(select 1 from user_completed_lessons cl where cl.user_id=:uid and cl.lesson_id=l.id) then 1 else 0 end done,
            case when exists(select 1 from quizzes q where q.lesson_id=l.id and q.required_for_progress=true
                and exists(select 1 from questions qu where qu.quiz_id=q.id)
                and not exists(select 1 from user_quiz_scores qs where qs.user_id=:uid and qs.lesson_id=l.id and qs.total>0 and floor(qs.score*100.0/qs.total+0.5)>=q.passing_score)) then 1 else 0 end quiz_missing,
            (select max(lp.updated_at) from lesson_progress lp where lp.user_id=:uid and lp.lesson_id=l.id) accessed
          from lessons l join chapters ch on ch.id=l.chapter_id where
        """+published("l")+" and "+published("ch")+"""
        ), base as (
          select c.*,
        """+"case when "+mandatory+" then 1 else 0 end mandatory,"+"""
            case when exists(select 1 from user_registered_courses rc where rc.user_id=:uid and rc.course_id=c.id) then 1 else 0 end registered,
            (select count(*) from lessons_visible l where l.course_id=c.id) total_count,
            (select count(*) from lessons_visible l where l.course_id=c.id and l.done=1) completed_count,
            (select max(l.accessed) from lessons_visible l where l.course_id=c.id) last_accessed_at,
            case when exists(select 1 from user_course_deadlines dd where dd.user_id=:uid and dd.course_id=c.id) then (select dd.due_date from user_course_deadlines dd where dd.user_id=:uid and dd.course_id=c.id) else c.completion_deadline end due_date,
            case when exists(select 1 from chapters ch where ch.course_id=c.id and
        """+published("ch")+"""
            ) and not exists(select 1 from chapters ch where ch.course_id=c.id and
        """+published("ch")+"""
            and (not exists(select 1 from lessons_visible l where l.chapter_id=ch.id) or exists(select 1 from lessons_visible l where l.chapter_id=ch.id and (l.done=0 or l.quiz_missing=1)))) then 1 else 0 end complete
          from visible c where
        """+published("c")+"""
        ), sources as (
          select b.*,case when mandatory=1 then 'ASSIGNED' when registered=1 then 'REGISTERED'
            when exists(select 1 from course_enrollment_requests er where er.user_id=:uid and er.course_id=b.id and er.status='PENDING') then 'PENDING' else 'AVAILABLE' end source
          from base b
        ), facts as (
          select s.*,case when source in ('PENDING','AVAILABLE') then source when complete=1 then 'COMPLETED'
            when last_accessed_at is not null or completed_count>0
              or exists(select 1 from quiz_attempts qa join quizzes q on q.id=qa.quiz_id join lessons l on l.id=q.lesson_id where qa.user_id=:uid and qa.total>0 and l.course_id=s.id)
              or exists(select 1 from user_quiz_scores qs where qs.user_id=:uid and qs.course_id=s.id)
            then 'IN_PROGRESS' else 'NOT_STARTED' end learning_status
          from sources s
        )
        """;
        return new Context(sql,p);
    }
    public Summary summary(User u) {
        var c=facts(u);
        return jdbc.queryForObject(c.sql()+"""
          select sum(case when source in ('ASSIGNED','REGISTERED') then 1 else 0 end) joined,
          sum(case when source in ('AVAILABLE','PENDING') then 1 else 0 end) discover,
          sum(case when learning_status='NOT_STARTED' then 1 else 0 end) not_started,
          sum(case when learning_status='IN_PROGRESS' then 1 else 0 end) in_progress,
          sum(case when learning_status='COMPLETED' then 1 else 0 end) completed,
          sum(mandatory) mandatory,sum(case when mandatory=1 and learning_status='COMPLETED' then 1 else 0 end) mandatory_complete,
          sum(case when source in ('ASSIGNED','REGISTERED') then completed_count else 0 end) done,
          sum(case when source in ('ASSIGNED','REGISTERED') then total_count else 0 end) total from facts
        """,c.params(),(r,n)->new Summary(r.getLong("joined"),r.getLong("discover"),r.getLong("not_started"),r.getLong("in_progress"),r.getLong("completed"),r.getLong("mandatory"),r.getLong("mandatory_complete"),r.getLong("done"),r.getLong("total"),r.getLong("total")==0?null:(int)Math.round(r.getLong("done")*100.0/r.getLong("total"))));
    }
    public static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.forLanguageTag("vi")),Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d');
    }
    public static String normalizedSql(String value) {
        String from="àáạảãăằắặẳẵâầấậẩẫèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ";
        String to="a".repeat(17)+"e".repeat(11)+"i".repeat(5)+"o".repeat(17)+"u".repeat(11)+"y".repeat(5)+"d";
        String sql="translate(normalize(lower("+value+"), NFC),'"+from+"','"+to+"')";
        for(char mark:new char[]{'\u0300','\u0301','\u0303','\u0309','\u0323','\u0306','\u0302','\u031b'}) sql="replace("+sql+",'"+mark+"','')";
        return sql;
    }
    // Vietnamese alphabet primary order: a, ă, â, b, c, d, đ, e, ê, ... o, ô, ơ, ... u, ư.
    // Explicit translation avoids relying on the database host's default collation.
    public static String vietnameseOrderSql(String expression) {
        String[] groups={"aàáạảã","ăằắặẳẵ","âầấậẩẫ","b","c","d","đ","eèéẹẻẽ","êềếệểễ","f","g","h","iìíịỉĩ","j","k","l","m","n","oòóọỏõ","ôồốộổỗ","ơờớợởỡ","p","q","r","s","t","uùúụủũ","ưừứựửữ","v","w","x","yỳýỵỷỹ","z"};
        StringBuilder from=new StringBuilder(),to=new StringBuilder();
        for(int i=0;i<groups.length;i++) {from.append(groups[i]);to.append(String.valueOf((char)('A'+i)).repeat(groups[i].length()));}
        return "translate(normalize(lower("+expression+"), NFC),'"+from+"','"+to+"') collate \"C\"";
    }
    public static String vietnameseToneSql(String expression) {
        String[] groups={"aàáạảã","ăằắặẳẵ","âầấậẩẫ","eèéẹẻẽ","êềếệểễ","iìíịỉĩ","oòóọỏõ","ôồốộổỗ","ơờớợởỡ","uùúụủũ","ưừứựửữ","yỳýỵỷỹ"};
        String from="bcdđfghjklmnpqrstvwxz",to="A".repeat(from.length());
        for(var group:groups){from+=group;to+="ABEFCD";}
        return "translate(normalize(lower("+expression+"), NFC),'"+from+"','"+to+"') collate \"C\"";
    }
    public Page page(User u,Filter f) {
        var c=facts(u);var p=new HashMap<>(c.params());
        String where=" where source "+("discover".equals(f.tab())?"in ('AVAILABLE','PENDING')":"in ('ASSIGNED','REGISTERED')");
        String[][] filters={{"learning_status",f.status()},{"domain_id",f.domainId()},{"business_module",f.businessModule()},{"program_id",f.programId()},{"level",f.level()},{"source",f.source()}};
        for(int i=0;i<filters.length;i++)if(filters[i][1]!=null&&!filters[i][1].isBlank()) {where+=" and "+filters[i][0]+"=:f"+i;p.put("f"+i,filters[i][1]);}
        if(f.mandatory())where+=" and mandatory=1";
        if(f.search()!=null&&!f.search().isBlank()) {
            where+=" and "+normalizedSql("coalesce(name,'') || ' ' || coalesce(code,'') || ' ' || coalesce(description,'') || ' ' || coalesce(keywords,'')")+" like :search escape '!'";
            p.put("search","%"+normalize(f.search().trim()).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        }
        long count=jdbc.queryForObject(c.sql()+" select count(*) from facts"+where,p,Long.class);
        int size=Math.max(1,Math.min(f.size(),60)),page=Math.max(1,f.page());p.put("limit",size);p.put("offset",(long)(page-1)*size);
        String order=switch(f.sort()==null?"":f.sort()) {case "name"->vietnameseOrderSql("name")+","+vietnameseToneSql("name")+",normalize(lower(name), NFC),id";case "recent"->"last_accessed_at desc nulls last,id";default->"case when learning_status<>'COMPLETED' and source in ('ASSIGNED','REGISTERED') then due_date else null end asc nulls last,last_accessed_at desc nulls last,id";};
        var items=jdbc.query(c.sql()+" select id,code,name,thumbnail,icon_name,program_id,program_name,domain_id,domain_name,business_module,level,source,mandatory,learning_status,completed_count,total_count,due_date,last_accessed_at from facts"+where+" order by "+order+" limit :limit offset :offset",p,(r,n)->{
            var dto=CourseDto.builder().id(r.getString("id")).code(r.getString("code")).name(r.getString("name")).thumbnail(r.getString("thumbnail")).iconName(r.getString("icon_name")).programId(r.getString("program_id")).programName(r.getString("program_name")).domainId(r.getString("domain_id")).domainName(r.getString("domain_name")).businessModule(r.getString("business_module")).level(r.getString("level")).build();
            int done=r.getInt("completed_count"),total=r.getInt("total_count");
            return new Card(dto,r.getString("source"),r.getInt("mandatory")==1,r.getString("learning_status"),done,total,total==0?0:(int)Math.round(done*100.0/total),r.getObject("due_date",LocalDate.class),r.getObject("last_accessed_at",LocalDateTime.class),null);
        });return new Page(items,count,page,size);
    }
    public Map<String,List<Map<String,String>>> choices(User u,String tab) {
        var c=facts(u);String where=" where source "+("discover".equals(tab)?"in ('AVAILABLE','PENDING')":"in ('ASSIGNED','REGISTERED')");
        Map<String,List<Map<String,String>>> result=new LinkedHashMap<>();List<String> selects=new ArrayList<>();
        for(var entry:Map.of("status","learning_status","source","source","domainId","domain_id","programId","program_id","businessModule","business_module","level","level").entrySet()) {
            result.put(entry.getKey(),new ArrayList<>());
            String column=entry.getValue(),label=column.equals("domain_id")?"domain_name":column.equals("program_id")?"program_name":column;
            selects.add("select distinct '"+entry.getKey()+"' choice_key,"+column+" choice_value,"+label+" choice_label from facts"+where+" and "+column+" is not null");
        }
        jdbc.query(c.sql()+String.join(" union all ",selects)+" order by choice_key,choice_value",c.params(),r->{
            result.get(r.getString("choice_key")).add(Map.of("value",r.getString("choice_value"),"label",r.getString("choice_label")));
        });
        return result;
    }
    public String resumeCourseId(User u) {
        var c=facts(u);var ids=jdbc.query(c.sql()+" select id from facts where source in ('ASSIGNED','REGISTERED') and learning_status<>'COMPLETED' and total_count>0 order by last_accessed_at desc nulls last,id limit 1",c.params(),(r,n)->r.getString(1));
        return ids.isEmpty()?null:ids.get(0);
    }
    public List<Map<String,String>> courseChoices(User u,String search,int size) {
        var c=facts(u);var p=new HashMap<>(c.params());p.put("limit",Math.max(1,Math.min(size,100)));
        p.put("choiceSearch","%"+normalize(search==null?"":search.trim()).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        return jdbc.query(c.sql()+" select id,name from visible c where (editor_access=1 or exists(select 1 from facts f where f.id=c.id and f.source in ('ASSIGNED','REGISTERED'))) and "+normalizedSql("name || ' ' || code")+" like :choiceSearch escape '!' order by "+normalizedSql("name")+",id limit :limit",p,(r,n)->Map.of("id",r.getString(1),"name",r.getString(2)));
    }

}
