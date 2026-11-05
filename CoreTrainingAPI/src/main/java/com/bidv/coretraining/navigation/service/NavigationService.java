package com.bidv.coretraining.navigation.service;

import com.bidv.coretraining.navigation.dto.NavigationItemDto;
import com.bidv.coretraining.user.entity.User;
import java.util.List;

public interface NavigationService {
    List<NavigationItemDto> navigation(User user);
}
