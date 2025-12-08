package com.example.post_service.model;

import lombok.Data;

@Data
public class Post {
    private String id;
    private String userId;
    private String content;
    private String imageUrl;   // URL pública devuelta por Supabase
    private long createdAt;

    public void setId(String id) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
