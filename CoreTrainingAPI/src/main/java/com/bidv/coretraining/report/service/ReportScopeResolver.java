package com.bidv.coretraining.report.service;

import com.bidv.coretraining.report.repository.TrainingReportQueryRepository;
import com.bidv.coretraining.report.repository.TrainingReportQueryRepository.RoleScope;
import com.bidv.coretraining.report.repository.TrainingReportQueryRepository.Scope;
import com.bidv.coretraining.report.repository.TrainingReportQueryRepository.Unit;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.user.entity.User;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * The data scope of a report request: the units (with their active descendants) of each role that grants one of
 * the accepted report pages. Scope and permission stay paired per role, so a role's units never widen another
 * role's report page. An actor without any accepted page gets an empty scope; callers that must refuse such an
 * actor check {@link #require} first.
 */
@Component
public class ReportScopeResolver {
    private final TrainingReportQueryRepository repository;

    public ReportScopeResolver(TrainingReportQueryRepository repository) {
        this.repository = repository;
    }

    public Scope resolve(User actor, Set<String> accepted) {
        boolean system = AccessPolicy.system(actor);
        if (system)
            return new Scope(true, actor.getId(), List.of());
        Map<String, Unit> units = new HashMap<>();
        repository.units().forEach(u -> units.put(u.id(), u));
        List<RoleScope> roles = new ArrayList<>();
        if (actor.isDynamicAccessInitialized()) {
            for (var role : AccessPolicy.policies(actor))
                if (role.active() && !role.learnerRole() && role.permissions().stream().anyMatch(accepted::contains))
                    roles.add(new RoleScope(descendants(units, role.unitIds()), role.assignedCoursesOnly()));
        } else {
            var roots = actor.getScopeUnitIds().isEmpty()
                    ? actor.getOrganizationUnit() == null ? Set.<String>of() : Set.of(actor.getOrganizationUnit().getId())
                    : actor.getScopeUnitIds();
            roles.add(new RoleScope(descendants(units, roots), false));
        }
        return new Scope(false, actor.getId(), roles);
    }

    /** Refuses an actor holding none of the accepted report pages. */
    public static void require(User actor, Set<String> accepted) {
        AccessPolicy.require(accepted.stream().anyMatch(p -> AccessPolicy.has(actor, p)));
    }

    /** Active units below (or equal to) one of the roots, through an active chain of parents. */
    private static Set<String> descendants(Map<String, Unit> units, Set<String> roots) {
        Set<String> out = new HashSet<>();
        for (String id : units.keySet())
            if (ancestry(units, id).stream().anyMatch(roots::contains))
                out.add(id);
        return out;
    }

    private static Set<String> ancestry(Map<String, Unit> units, String start) {
        Set<String> ids = new HashSet<>();
        for (String id = start; id != null;) {
            var u = units.get(id);
            if (u == null || !u.active() || !ids.add(id))
                return Set.of();
            id = u.parent();
        }
        return ids;
    }

    /** Every active ancestor (and the unit itself) of the scope units: grants made there reach the scope. */
    public Set<String> grantUnits(Collection<String> scopeUnits) {
        Map<String, Unit> units = new HashMap<>();
        repository.units().forEach(u -> units.put(u.id(), u));
        Set<String> out = new HashSet<>();
        for (String id : scopeUnits)
            out.addAll(ancestry(units, id));
        return out;
    }
}
