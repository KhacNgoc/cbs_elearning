package com.bidv.coretraining.repository;

import com.bidv.coretraining.dto.LeaderboardReadDto.Page;
import java.util.List;
import java.util.Set;

public interface LeaderboardQueryRepository {
    record Unit(String id, String parent, boolean active) {}
    record Scope(boolean unrestricted, boolean withoutUnit, Set<String> unitIds) {
        public Scope { unitIds = Set.copyOf(unitIds); }
    }
    List<Unit> units();
    Page page(Long actorId, Scope scope, int page, int size);
}
