package com.example.user_service.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FollowActionResponse {
    String targetUserId;
    int followersCount;
    int followingCount;
    boolean following;
}
