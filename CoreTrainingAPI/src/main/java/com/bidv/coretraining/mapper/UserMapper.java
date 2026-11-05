package com.bidv.coretraining.mapper;
import com.bidv.coretraining.leaderboard.dto.UserBadgeDto;

import com.bidv.coretraining.leaderboard.entity.UserBadge;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "badge.id", target = "id")
    @Mapping(source = "badge.title", target = "title")
    @Mapping(source = "badge.type", target = "type")
    @Mapping(source = "badge.course.id", target = "courseId")
    @Mapping(source = "badge.course.name", target = "courseName")
    @Mapping(source = "unlockedAt", target = "unlockedAt", dateFormat = "yyyy-MM-dd")
    @Mapping(source = "badge.icon", target = "icon")
    UserBadgeDto toUserBadgeDto(UserBadge userBadge);

    List<UserBadgeDto> toUserBadgeDtoList(List<UserBadge> userBadges);
}
