package com.example.post_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfile {
    private String id;
    private String name;
    private String avatarUrl;

    public static UserProfile fallback(String id) {
        return new UserProfile(id, null, null);
    }
}
