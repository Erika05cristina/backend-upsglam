package com.example.post_service.dto;

import java.util.List;

import lombok.Data;

@Data
public class FollowingListResponse {
    private String userId;
    private int count;
    private List<String> following;
}
