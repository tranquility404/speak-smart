package com.tranquility.SpeakSmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranquility.SpeakSmart.model.AnalysisRequest;
import com.tranquility.SpeakSmart.model.AnalysisResult;
import com.tranquility.SpeakSmart.repository.AnalysisRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class AsyncAudioProcessingService {

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private  UserService userService;

    @Autowired
    private SpeechAnalysisService audioAnalysisService;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private AIService aiService;

    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final int MAX_RETRIES = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 180;  // 30 mins

    /**
     * Process audio analysis asynchronously
     */
    @Async("audioProcessingTaskExecutor")
    public CompletableFuture<Void> processAudioAnalysisAsync(String requestId) {
        log.info("Starting async audio processing for request: {}", requestId);

        try {
            AnalysisRequest request = analysisRequestRepository.findById(requestId)
                    .orElseThrow(() -> new IllegalArgumentException("Analysis request not found: " + requestId));

            // Mark as processing
            request.markAsProcessing();
            analysisRequestRepository.save(request);

            // Download audio file from Cloudinary
            byte[] audioData = downloadAudioFile(request.getAudioUrl());    // this might become issue, check later. As of now we are loading whole file in memory.

            // Create temporary multipart file for processing
            MultipartFile audioFile = createMultipartFile(audioData, request.getFileName(), request.getFileContentType());

            // Use real audio analysis service instead of mock
            AnalysisResult result = audioAnalysisService.analyzeAudio(audioFile, request);

            // Save analysis result as JSON to Cloudinary
            String analysisJson = objectMapper.writeValueAsString(result);
            Map<String, Object> uploadResult = uploadAnalysisResult(analysisJson, request.getId());

            // Update request with results
            String analysisResultUrl = (String) uploadResult.get("secure_url");
            String analysisResultPublicId = (String) uploadResult.get("public_id");

            request.markAsCompleted(analysisResultUrl, analysisResultPublicId);

            // Set quick results for fast API responses
            request.setQuickResults(createQuickResults(result));

            analysisRequestRepository.save(request);
            userService.updateAnalysisPoints(request.getUserId(), request.getCompletedAt());

            log.info("Audio processing completed successfully for request: {}", requestId);

        } catch (Exception e) {
            log.error("Error processing audio for request: {}", requestId, e);
            handleProcessingError(requestId, e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle processing errors with retry logic
     */
    private void handleProcessingError(String requestId, String errorMessage) {
        try {
            AnalysisRequest request = analysisRequestRepository.findById(requestId).orElse(null);
            if (request != null) {
                if (request.getRetryCount() < MAX_RETRIES) {
                    request.incrementRetryCount();
                    analysisRequestRepository.save(request);
                    log.info("Marked request {} for retry (attempt {})", requestId, request.getRetryCount());
                } else {
                    request.markAsFailed(errorMessage);
                    analysisRequestRepository.save(request);
                    log.error("Request {} failed after {} retries", requestId, MAX_RETRIES);
                }
            }
        } catch (Exception e) {
            log.error("Error handling processing error for request: {}", requestId, e);
        }
    }

    /**
     * Download audio file from Cloudinary URL
     */
    private byte[] downloadAudioFile(String audioUrl) throws IOException {
        try {
            return restTemplate.getForObject(audioUrl, byte[].class);
        } catch (Exception e) {
            throw new IOException("Failed to download audio file from: " + audioUrl, e);
        }
    }

    /**
     * Upload analysis result JSON to Cloudinary
     */
    private Map<String, Object> uploadAnalysisResult(String analysisJson, String requestId) throws IOException {
        String fileName = "analysis_result_" + requestId + ".json";
        String folder = "analysis_results";

        Map<String, Object> result = cloudinaryService.uploadFile(analysisJson, fileName, folder, "raw");
        log.info("Analysis result uploaded for request: {}", requestId);
        return result;
    }

    /**
     * Create MultipartFile from byte array
     */
    private MultipartFile createMultipartFile(byte[] audioData, String fileName, String contentType) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return fileName;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public boolean isEmpty() {
                return audioData == null || audioData.length == 0;
            }

            @Override
            public long getSize() {
                return audioData != null ? audioData.length : 0;
            }

            @Override
            public byte[] getBytes() throws IOException {
                return audioData;
            }

            @Override
            public java.io.InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(audioData);
            }

            @Override
            public void transferTo(File dest) throws IOException, IllegalStateException {
                try (FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                    fos.write(audioData);
                }
            }
        };
    }

    /**
     * Create quick results for fast API responses
     */
    private Map<String, Object> createQuickResults(AnalysisResult result) {
        Map<String, Object> quickResults = new HashMap<>();

        // Speech Rate Analysis
        if (result.getSpeechRate() != null) {
            quickResults.put("average_wpm", result.getSpeechRate().getAvgSpeechRate());
            quickResults.put("speech_rate_score", result.getSpeechRate().getScore());
            quickResults.put("speech_rate_chart_url", result.getSpeechRate().getChartUrl());
        }

        // Intonation Analysis  
        if (result.getIntonation() != null) {
            quickResults.put("pitch_variation", result.getIntonation().getPitchVariation());
            quickResults.put("intonation_score", result.getIntonation().getScore());
            quickResults.put("intonation_chart_url", result.getIntonation().getChartUrl());
        }

        // Energy Analysis
        if (result.getEnergy() != null) {
            quickResults.put("average_energy", result.getEnergy().getAverageEnergy());
            quickResults.put("energy_score", result.getEnergy().getScore());
        }

        // Pause Analysis
        if (result.getPauses() != null) {
            quickResults.put("total_pauses", result.getPauses().getTotalPauses());
            quickResults.put("pause_score", result.getPauses().getScore());
        }

        // Overall Score
        quickResults.put("overall_score", result.getOverallScore());

        // Audio Metadata
        if (result.getAudioMetadata() != null) {
            quickResults.put("duration_seconds", result.getAudioMetadata().getDurationSeconds());
        }

        // AI Analysis Quick Info
//        if (result.getAiAnalysis() != null) {
//            quickResults.put("ai_analysis_available", true);
//            quickResults.put("transcription_preview",
//                    result.getAiAnalysis().get("transcription") != null
//                    ? result.getAiAnalysis().get("transcription").toString().substring(0, Math.min(100, result.getAiAnalysis().get("transcription").toString().length())) + "..."
//                    : "No transcription available");
//        } else {
//            quickResults.put("ai_analysis_available", false);
//        }

        return quickResults;
    }

    /**
     * Scheduled task to retry failed requests
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void retryFailedRequests() {
        List<AnalysisRequest> failedRequests = analysisRequestRepository
                .findFailedRequestsForRetry(AnalysisRequest.AnalysisStatus.RETRY, MAX_RETRIES);

        for (AnalysisRequest request : failedRequests) {
            log.info("Retrying failed request: {}", request.getId());
            processAudioAnalysisAsync(request.getId());
        }
    }

    /**
     * Scheduled task to clean up stuck processing requests
     */
    @Scheduled(fixedDelay = 600000) // Every 10 minutes
    public void cleanupStuckRequests() {
        Instant cutoffTime = Instant.now().minusSeconds(PROCESSING_TIMEOUT_SECONDS);
        List<AnalysisRequest> stuckRequests = analysisRequestRepository
                .findStuckProcessingRequests(cutoffTime);

        for (AnalysisRequest request : stuckRequests) {
            log.warn("Found stuck processing request: {}, marking as failed", request.getId());
            request.markAsFailed("Processing timeout exceeded");
            analysisRequestRepository.save(request);
        }
    }
}
