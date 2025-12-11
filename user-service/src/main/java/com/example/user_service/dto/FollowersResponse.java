package com.example.user_service.dto;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FollowersResponse {
    String userId;
    int count;
    List<String> followers;
}
