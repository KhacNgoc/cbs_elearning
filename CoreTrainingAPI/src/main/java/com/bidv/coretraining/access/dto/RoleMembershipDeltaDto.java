package com.bidv.coretraining.access.dto;

import java.util.*;

/** Data contracts for RoleMembershipDeltaService. */
public final class RoleMembershipDeltaDto {
    private RoleMembershipDeltaDto() {}

    public record Changes(Set<Long> addUserIds, Set<Long> removeUserIds, Long version) {}
}
