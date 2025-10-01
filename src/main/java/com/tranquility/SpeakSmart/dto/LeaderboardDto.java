package com.tranquility.SpeakSmart.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class LeaderboardDto {
    private String _id;
    private String name;
    private String profilePicCloudUrl;
    private int points;
    private int analysisCount;
    private int streak;
    private Instant lastAnalysis;
}
