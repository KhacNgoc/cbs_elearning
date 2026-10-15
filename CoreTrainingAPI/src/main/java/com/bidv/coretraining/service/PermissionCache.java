package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.security.RolePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PermissionCache {
    private record Entry(long revision, long expires, List<RolePolicy> policies) {
    }

    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public long revision() {
        return revision.get();
    }

    public void attach(User u) {
        if (!u.isDynamicAccessInitialized() || u.getId() == null)
            return;
        long generation = revision.get(), now = System.currentTimeMillis();
        Entry hit = entries.get(u.getId());
        if (hit != null && hit.revision() == generation && hit.expires() > now) {
            u.setPolicySnapshot(hit.policies());
            return;
        }
        var value = u.getAssignedRoles().stream().map(RolePolicy::of).toList();
        // Never cache a value loaded across an invalidation.
        if (generation == revision.get()) {
            if (entries.size() > 10000)
                entries.clear();
            entries.put(u.getId(), new Entry(generation, now + 60000, value));
        }
        u.setPolicySnapshot(value);
    }

    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate();
                }
            });
        else
            invalidate();
    }

    public void invalidate() {
        revision.incrementAndGet();
        entries.clear();
    }
}
