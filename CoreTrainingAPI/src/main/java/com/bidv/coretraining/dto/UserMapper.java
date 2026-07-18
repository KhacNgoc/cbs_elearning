package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.UserBadge;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "badge.id", target = "id")
    @Mapping(source = "badge.title", target = "title")
    @Mapping(source = "badge.type", target = "type")
    @Mapping(source = "badge.topic.id", target = "topicId")
    @Mapping(source = "badge.topic.name", target = "topicName")
    @Mapping(source = "unlockedAt", target = "unlockedAt", dateFormat = "yyyy-MM-dd")
    @Mapping(source = "badge.icon", target = "icon")
    UserBadgeDto toUserBadgeDto(UserBadge userBadge);

    List<UserBadgeDto> toUserBadgeDtoList(List<UserBadge> userBadges);
}
