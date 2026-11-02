package com.bidv.coretraining.service.impl;
import com.bidv.coretraining.service.*;
import com.bidv.coretraining.dto.TrainingReportQueryDto.Filter;
import com.bidv.coretraining.repository.TrainingReportQueryRepository;
import com.bidv.coretraining.repository.TrainingReportQueryRepository.*;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service
@Transactional(readOnly=true)
public class TrainingReportReadServiceImpl implements TrainingReportReadService {
    private final AuthorizationService auth;
    private final TrainingReportQueryRepository repository;
    public TrainingReportReadServiceImpl(AuthorizationService auth, TrainingReportQueryRepository repository) {this.auth=auth;this.repository=repository;}
    private Filter filter(Map<String,String> p) {
        try { return new Filter(p.get("from"),p.get("to"),p.get("unit"),p.get("group"),p.get("program"),p.get("course"),p.get("status"),p.getOrDefault("view","completion"),p.get("action"),p.get("search"),Integer.parseInt(p.getOrDefault("page","0")),Integer.parseInt(p.getOrDefault("size","20")),p.get("dimension"),p.get("key")); }
        catch(NumberFormatException e) { throw new BadRequestException("Tham số phân trang không hợp lệ"); }
    }
    private void validate(Filter f) {
        LocalDate today=LocalDate.now();
        LocalDate from=f.from()==null || f.from().isBlank() ? null : LocalDate.parse(f.from());
        if(from!=null && from.isAfter(today)) throw new BadRequestException("Khoảng ngày không hợp lệ");
        LocalDate to=f.to()==null || f.to().isBlank() ? null : LocalDate.parse(f.to());
        if(to!=null && (to.isAfter(today) || from!=null && from.isAfter(to))) throw new BadRequestException("Khoảng ngày không hợp lệ");
    }
    private User actor() {auth.requireOperation("overview","read");return auth.current();}
    private Scope scope(User actor) {
        Map<String,Unit> units=new HashMap<>(); repository.units().forEach(u->units.put(u.id(),u));
        java.util.function.Function<String,Set<String>> ancestry=id->{
            Set<String> ids=new HashSet<>();
            while(id!=null) {var u=units.get(id);if(u==null || !u.active() || !ids.add(id)) return Set.of(); id=u.parent();}
            return ids;
        };
        java.util.function.Function<Set<String>,Set<String>> descendants=roots->{
            Set<String> out=new HashSet<>(); for(String id:units.keySet()) if(ancestry.apply(id).stream().anyMatch(roots::contains)) out.add(id); return out;
        };
        List<RoleScope> roles=new ArrayList<>();
        if(!AccessPolicy.system(actor)) {
            if(actor.isDynamicAccessInitialized()) {
                for(var role:AccessPolicy.policies(actor)) if(role.active() && !role.learnerRole() && role.permissions().contains("overview:read"))
                    roles.add(new RoleScope(descendants.apply(role.unitIds()),role.assignedCoursesOnly()));
            } else {
                var roots=actor.getScopeUnitIds().isEmpty() ? actor.getOrganizationUnit()==null ? Set.<String>of() : Set.of(actor.getOrganizationUnit().getId()) : actor.getScopeUnitIds();
                roles.add(new RoleScope(descendants.apply(roots),false));
            }
        }
        return new Scope(AccessPolicy.system(actor),actor.getId(),roles);
    }
    public Map<String,Object> read(Map<String,String> params) {
        var actor=actor(); var f=filter(params); var scope=scope(actor); validate(f);
        return repository.read(scope,f,!"false".equals(params.get("metadata")));
    }
    public Map<String,Object> read(Filter f) {return read(f,true);}
    public Map<String,Object> read(Filter f,boolean metadata) {var scope=scope(actor());validate(f);return repository.read(scope,f,metadata);}
    public void export(Map<String,String> params,java.io.Writer writer) throws java.io.IOException {
        var actor=actor(); var f=filter(params); var scope=scope(actor);validate(f);export(scope,f,writer);
    }
    public void export(Filter f,java.io.Writer writer) throws java.io.IOException {var scope=scope(actor());validate(f);export(scope,f,writer);}
    private void export(Scope scope,Filter f,java.io.Writer writer) throws java.io.IOException {
        writer.write('\ufeff');csvLine(writer,csvHeaders(f.view()));
        repository.export(scope,f,row->{try {csvLine(writer,csvCells(row,f.view()));} catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}});
        writer.flush();
    }
    private static List<?> csvHeaders(String view) {
        if ("scores".equals(view))
            return List.of("Học viên", "Khóa học", "Số lần thi", "TB điểm gần nhất /100", "Kết quả qua các lần thi");
        if ("domains".equals(view))
            return List.of("Lĩnh vực", "Học viên", "Lượt học", "Tỷ lệ hoàn thành", "Đạt kiểm tra / đã thi",
                    "Điểm kiểm tra TB /100");
        if ("learners".equals(view))
            return List.of("Học viên", "Khóa được giao / tham gia", "Hoàn thành", "Tỷ lệ hoàn thành",
                    "Đạt kiểm tra / đã thi", "Điểm TB /100");
        if ("programs".equals(view))
            return List.of("Chương trình", "Học viên", "Khóa bắt buộc đã giao", "Bắt buộc hoàn thành",
                    "Tiến độ chương trình", "Đạt kiểm tra / đã thi");
        if ("courses".equals(view))
            return List.of("Khóa học", "Ghi danh", "Hoàn thành", "Tỷ lệ hoàn thành", "Đạt kiểm tra / đã thi",
                    "Điểm TB /100", "Bỏ dở");
        if ("groups".equals(view) || "units".equals(view))
            return List.of("Đối tượng", "Học viên", "Lượt học", "Tỷ lệ hoàn thành", "Tỷ lệ đạt kiểm tra",
                    "Học viên quá hạn / có hạn");
        return List.of("Học viên", "Đơn vị / nhóm", "Khóa học", "Trạng thái", "Hạn hoàn thành", "Kết quả kiểm tra");
    }

    private static long number(Map<String, Object> r, String name) {
        return r.get(name) instanceof Number n ? n.longValue() : 0;
    }

    private static String ratio(long n, long d) {
        return d == 0 ? "—" : Math.round(n * 100.0 / d) + "%";
    }

    private static List<?> csvCells(Map<String, Object> r, String view) {
        String label = Objects.toString(r.get("name"), ""), avg = r.get("average") == null ? "Chưa đánh giá"
                : String.format(Locale.ROOT, "%.1f", ((Number) r.get("average")).doubleValue());
        long total = number(r, "total"), complete = number(r, "complete"), mandatory = number(r, "mandatory"),
                mandatoryComplete = number(r, "mandatoryComplete"), tested = number(r, "tested"),
                passed = number(r, "passed");
        String pass = passed + " / " + tested + " (" + ratio(passed, tested) + ")";
        return switch (view == null ? "" : view) {
            case "domains" -> List.of(label, number(r, "people"), total, ratio(complete, total), pass, avg);
            case "learners" -> List.of(r.get("learnerName") + " · " + label, mandatory + " / " + total, complete,
                    ratio(complete, total), pass, avg);
            case "programs" -> List.of(label, number(r, "people"), mandatory, mandatoryComplete,
                    ratio(mandatoryComplete, mandatory), pass);
            case "courses" -> List.of(label, total, complete, ratio(complete, total), pass, avg, "Chưa có dữ liệu");
            case "groups",
                    "units" ->
                List.of(label, number(r, "people"), total, ratio(complete, total), pass,
                        number(r, "overduePeople") + " / " + number(r, "peopleWithDeadline") + " ("
                                + ratio(number(r, "overduePeople"), number(r, "peopleWithDeadline")) + ")");
            case "scores" ->
                List.of(label + " · " + r.get("learnerId"), r.get("courseName"), number(r, "attemptCount"), avg,
                        number(r, "attemptCount") == 0 ? "Chưa kiểm tra"
                                : number(r, "everPassed") == 1 && number(r, "requiredPassed") == 1 ? "Đã từng đạt"
                                        : "Chưa từng đạt");
            default -> List.of(label + " · " + r.get("learnerId"), r.get("unit") + " / " + r.get("groupName"),
                    r.get("courseName"), r.get("status"), Objects.toString(r.get("dueDate"), "Chưa quy định"), avg);
        };
    }

    private static void csvLine(java.io.Writer writer, List<?> cells) throws java.io.IOException {
        boolean first = true;
        for (Object cell : cells) {
            if (!first)
                writer.write(',');
            first = false;
            String value = Objects.toString(cell, "");
            if (value.matches("(?s)^\\s*[=+@-].*") || value.startsWith("\t") || value.startsWith("\r")
                    || value.startsWith("\n"))
                value = "'" + value;
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }

    public Map<String,Object> attempts(String learner,String course,String before,int size) {
        var scope=scope(actor()); long learnerId=Long.valueOf(learner);
        String context = UUID
                .nameUUIDFromBytes((learner + "\\0" + course).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
        AttemptBoundary boundary = null;
        if (before != null && !before.isBlank()) {
            try {
                String[] key = new String(Base64.getUrlDecoder().decode(before),
                        java.nio.charset.StandardCharsets.UTF_8).split("\\|", -1);
                if (key.length != 3 || !key[0].equals(context))
                    throw new IllegalArgumentException();
                boundary = new AttemptBoundary(LocalDateTime.parse(key[1]),Long.valueOf(key[2]));
            } catch (RuntimeException e) {
                throw new com.bidv.coretraining.exception.BadRequestException("Cursor không hợp lệ");
            }
        }
        int limit=Math.max(1,Math.min(size,100));
        var rows=repository.attempts(scope,learnerId,course,boundary,limit);
        boolean more = rows.size() > limit;
        var items = new ArrayList<>(rows.subList(0, Math.min(limit, rows.size())));
        String cursor = "";
        if (more) {
            var last = items.get(items.size() - 1);
            cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (context + "|" + last.get("takenAt").toString().replace(' ', 'T') + "|" + last.get("id"))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return Map.of("items", items, "hasMore", more, "nextCursor", cursor);
    }
}
