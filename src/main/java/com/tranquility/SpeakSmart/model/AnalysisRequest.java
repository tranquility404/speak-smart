package com.tranquility.SpeakSmart.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Document(collection = "analysis_requests")
public class AnalysisRequest {

    @Id
    private String id;

    private String userId;
    private String audioUrl;          // Cloudinary URL
    private String audioPublicId;     // Cloudinary public ID
    private String fileName;
    private String fileContentType;
    private Long fileSizeBytes;
    private Double audioDurationSeconds;

    private AnalysisStatus status;
    private String errorMessage;
    private Integer retryCount = 0;

    private LocalDateTime requestedAt;
    private LocalDateTime processingStartedAt;
    private LocalDateTime completedAt;

    // Analysis results
    private String analysisResultUrl;    // URL to JSON file in Cloudinary
    private String analysisResultPublicId;
    private Map<String, Object> quickResults;  // Basic results for quick display

    public enum AnalysisStatus {
        PENDING, // Just uploaded, waiting to process
        PROCESSING, // Currently being analyzed
        COMPLETED, // Analysis finished successfully
        FAILED, // Analysis failed
        RETRY           // Retrying after failure
    }

    public AnalysisRequest() {
        this.requestedAt = LocalDateTime.now();
        this.status = AnalysisStatus.PENDING;
    }

    public void markAsProcessing() {
        this.status = AnalysisStatus.PROCESSING;
        this.processingStartedAt = LocalDateTime.now();
    }

    public void markAsCompleted(String analysisResultUrl, String analysisResultPublicId) {
        this.status = AnalysisStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.analysisResultUrl = analysisResultUrl;
        this.analysisResultPublicId = analysisResultPublicId;
    }

    public void markAsFailed(String errorMessage) {
        this.status = AnalysisStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.status = AnalysisStatus.RETRY;
    }
}
