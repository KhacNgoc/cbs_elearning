package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.NavigationItemDto;
import com.bidv.coretraining.entity.User;
import java.util.List;

public interface NavigationService {
    List<NavigationItemDto> navigation(User user);
}
