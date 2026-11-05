package com.bidv.coretraining.security;
import com.bidv.coretraining.access.entity.AccessRole;
import com.bidv.coretraining.access.service.PermissionCache;
import com.bidv.coretraining.access.service.impl.PermissionCacheImpl;
import com.bidv.coretraining.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PermissionCacheTest {
    @Test void snapshotsAreImmutableAndHitsAvoidReloadingRolePermissions() {
        PermissionCache cache=new PermissionCacheImpl();AccessRole r=AccessRole.builder().id("R").code("DYNAMIC").name("Dynamic").permissions(new HashSet<>(Set.of("courses:read"))).unitIds(new HashSet<>(Set.of("A"))).build();
        User u=spy(User.builder().id(7L).dynamicAccessInitialized(true).assignedRoles(new HashSet<>(Set.of(r))).build());
        cache.attach(u);cache.attach(u);verify(u,times(1)).getAssignedRoles();
        r.getPermissions().add("courses:update");assertThat(AccessPolicy.permissions(u)).doesNotContain("courses:update");
        assertThatThrownBy(()->u.getPolicySnapshot().get(0).permissions().add("x")).isInstanceOf(UnsupportedOperationException.class);
        cache.invalidate();cache.attach(u);verify(u,times(2)).getAssignedRoles();assertThat(AccessPolicy.permissions(u)).contains("courses:update");
    }
    @Test void invalidationRunsAfterCommitAndRollbackPreservesCachedGeneration(){
        PermissionCache cache=new PermissionCacheImpl();TransactionSynchronizationManager.initSynchronization();
        try{cache.invalidateAfterCommit();assertThat(cache.revision()).isZero();var callbacks=TransactionSynchronizationManager.getSynchronizations();callbacks.forEach(TransactionSynchronization::afterCommit);assertThat(cache.revision()).isEqualTo(1);}finally{TransactionSynchronizationManager.clearSynchronization();}
        TransactionSynchronizationManager.initSynchronization();
        try{cache.invalidateAfterCommit();TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));assertThat(cache.revision()).isEqualTo(1);}finally{TransactionSynchronizationManager.clearSynchronization();}
    }
}
