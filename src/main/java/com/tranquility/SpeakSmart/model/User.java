package com.tranquility.SpeakSmart.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Document(collection = "users")
@Data
public class User {
    @Id
    private String id;
    private String profilePicCloudUrl;
    private String username;
    private String name;
    private String email;
    private String password;
    private Set<String> roles;
    private int points;
    private int analysisCount;
    private int streak;
    private Instant lastAnalysis;
    // Constructors, getters, and setters
}

