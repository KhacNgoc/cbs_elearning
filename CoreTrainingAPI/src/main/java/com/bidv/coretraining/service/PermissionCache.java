package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.User;
import org.springframework.transaction.support.*;
import java.util.*;

/** Business operations for PermissionCache. */
public interface PermissionCache {
    public long revision();

    public void attach(User u);

    public void invalidateAfterCommit();

    public void invalidate();
}
