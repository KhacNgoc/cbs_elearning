package com.bidv.coretraining.access.service;

import com.bidv.coretraining.access.dto.RoleMembershipDeltaDto.*;
import java.util.*;

/** Business operations for RoleMembershipDeltaService. */
public interface RoleMembershipDeltaService {
    public void apply(String id, Changes changes);
}
