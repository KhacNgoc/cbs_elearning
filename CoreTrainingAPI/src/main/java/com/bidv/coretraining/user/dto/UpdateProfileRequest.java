package com.bidv.coretraining.user.dto;

/** Fields left null are not changed. */
public record UpdateProfileRequest(String fullName, String email, String avatarColor) {
}
