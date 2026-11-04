package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.RoleMembershipDeltaDto.*;
import java.util.*;

/** Business operations for RoleMembershipDeltaService. */
public interface RoleMembershipDeltaService {
    public void apply(String id, Changes changes);
}
