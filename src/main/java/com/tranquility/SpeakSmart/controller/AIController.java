package com.tranquility.SpeakSmart.controller;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.tranquility.SpeakSmart.model.ValidationResult;
import com.tranquility.SpeakSmart.util.AudioUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranquility.SpeakSmart.model.AnalysisRequest;
import com.tranquility.SpeakSmart.repository.AnalysisRequestRepository;
import com.tranquility.SpeakSmart.service.AIService;
import com.tranquility.SpeakSmart.service.AsyncAudioProcessingService;
import com.tranquility.SpeakSmart.service.CloudinaryService;
import com.tranquility.SpeakSmart.service.UserService;
import com.tranquility.SpeakSmart.util.LlmUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AIController {

    @Autowired
    private AIService aiService;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private UserService userService;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private AsyncAudioProcessingService asyncProcessingService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Optimized audio upload endpoint 1. Validates and uploads audio to
     * Cloudinary immediately 2. Creates analysis request in DB with PENDING
     * status 3. Starts async processing 4. Returns immediately with request ID
     * for status tracking
     */
    @PostMapping("/upload-audio")
    public ResponseEntity<?> uploadAudio(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Audio file is required"));
        }

        try {
            String userId = userService.getCurrentUserId();
            log.info("Processing audio upload for user: {}, file: {}", userId, file.getOriginalFilename());

            // Validate audio file
            log.info("Starting audio file validation...");
            ValidationResult validation = AudioUtils.validateAudioFile(file);
            if (!validation.isValid()) {
                log.error("Audio file validation failed: {}", validation.getErrorMessage());
                return ResponseEntity.badRequest().body(Map.of("error", validation.getErrorMessage()));
            }

            log.info("Audio file validation successful");

            // Upload to Cloudinary immediately
            log.info("Starting Cloudinary upload...");
            long uploadStart = System.currentTimeMillis();
            Map<String, Object> uploadResult = cloudinaryService.uploadAudio(validation.getAudioFile(), file.getContentType());
            long uploadTime = System.currentTimeMillis() - uploadStart;
            log.info("Cloudinary upload completed in {} ms", uploadTime);

            String audioUrl = (String) uploadResult.get("secure_url");
            String audioPublicId = (String) uploadResult.get("public_id");

            // Create analysis request in DB
            AnalysisRequest request = new AnalysisRequest();
            request.setUserId(userId);
            request.setAudioUrl(audioUrl);
            request.setAudioPublicId(audioPublicId);
            request.setFileName(file.getOriginalFilename());
            request.setFileContentType(file.getContentType());
            request.setFileSizeBytes(file.getSize());
            request.setAudioDurationSeconds(validation.getDurationSeconds());

            // Save request
            AnalysisRequest savedRequest = analysisRequestRepository.save(request);

            // Start async processing
            asyncProcessingService.processAudioAnalysisAsync(savedRequest.getId());

            // Return immediate response
            Map<String, Object> response = new HashMap<>();
            response.put("request_id", savedRequest.getId());
            response.put("status", "PENDING");
            response.put("message", "Audio uploaded successfully. Analysis in progress.");
            response.put("audio_url", audioUrl);
            response.put("duration_seconds", validation.getDurationSeconds());
            response.put("upload_time_ms", uploadTime);
            response.put("estimated_processing_time_seconds", estimateProcessingTime(validation.getDurationSeconds()));

            log.info("Audio upload completed for request: {} in {}ms", savedRequest.getId(), uploadTime);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing audio upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process audio: " + e.getMessage()));
        }
    }

    /**
     * Get analysis status and results
     */
    @GetMapping("/analysis/{requestId}")
    public ResponseEntity<?> getAnalysisStatus(@PathVariable String requestId) {
        try {
            String userId = userService.getCurrentUserId();

            Optional<AnalysisRequest> optionalRequest = analysisRequestRepository.findByIdAndUserId(requestId, userId);
            if (optionalRequest.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            AnalysisRequest request = optionalRequest.get();

            Map<String, Object> response = new HashMap<>();
            response.put("request_id", request.getId());
            response.put("status", request.getStatus());
            response.put("requested_at", request.getRequestedAt());

            switch (request.getStatus()) {
                case PENDING:
                    response.put("message", "Analysis is queued for processing");
                    break;
                case PROCESSING:
                    response.put("message", "Analysis in progress");
                    response.put("processing_started_at", request.getProcessingStartedAt());
                    long processingTime = java.time.Duration.between(
                            request.getProcessingStartedAt(), Instant.now()).toMillis();
                    response.put("processing_time_ms", processingTime);
                    break;
                case COMPLETED:
                    response.put("message", "Analysis completed");
                    response.put("completed_at", request.getCompletedAt());
                    response.put("audio_url", request.getAudioUrl());  // Include original audio URL
                    response.put("analysis_result_url", request.getAnalysisResultUrl());
                    response.put("quick_results", request.getQuickResults());

                    // Extract chart URLs from quick results for easy frontend access
                    Map<String, Object> quickResults = request.getQuickResults();
                    if (quickResults != null) {
                        Map<String, String> chartUrls = new HashMap<>();
                        if (quickResults.containsKey("speech_rate_chart_url")) {
                            chartUrls.put("speech_rate", (String) quickResults.get("speech_rate_chart_url"));
                        }
                        if (quickResults.containsKey("intonation_chart_url")) {
                            chartUrls.put("intonation", (String) quickResults.get("intonation_chart_url"));
                        }
                        response.put("chart_urls", chartUrls);
                    }
                    break;
                case FAILED:
                    response.put("message", "Analysis failed");
                    response.put("error", request.getErrorMessage());
                    response.put("retry_count", request.getRetryCount());
                    break;
                case RETRY:
                    response.put("message", "Analysis failed, retrying");
                    response.put("retry_count", request.getRetryCount());
                    break;
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting analysis status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get analysis status: " + e.getMessage()));
        }
    }

    /**
     * Get user's analysis history
     */
    @GetMapping("/analysis/history")
    public ResponseEntity<?> getAnalysisHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            String userId = userService.getCurrentUserId();

            PageRequest pageRequest = PageRequest.of(page, size,
                    Sort.by(Sort.Direction.DESC, "requestedAt"));

            List<AnalysisRequest> requests = analysisRequestRepository
                    .findByUserIdOrderByRequestedAtDesc(userId);

            // Limit the results to simulate pagination (implement proper pagination in repository)
            int fromIndex = page * size;
            int toIndex = Math.min(fromIndex + size, requests.size());

            List<AnalysisRequest> paginatedRequests = requests.subList(fromIndex, toIndex);

            List<Map<String, Object>> history = paginatedRequests.stream()
                    .map(this::createHistoryItem)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("history", history);
            response.put("page", page);
            response.put("size", size);
            response.put("total", requests.size());
            response.put("has_more", toIndex < requests.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting analysis history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get analysis history: " + e.getMessage()));
        }
    }

    /**
     * Download full analysis result
     */
    @GetMapping("/analysis/{requestId}/download")
    public ResponseEntity<?> downloadAnalysisResult(@PathVariable String requestId) {
        try {
            String userId = userService.getCurrentUserId();

            Optional<AnalysisRequest> optionalRequest = analysisRequestRepository.findByIdAndUserId(requestId, userId);
            if (optionalRequest.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            AnalysisRequest request = optionalRequest.get();

            if (request.getStatus() != AnalysisRequest.AnalysisStatus.COMPLETED) {
                return ResponseEntity.badRequest().body(Map.of("error", "Analysis not completed yet"));
            }

            // Return the Cloudinary URL for the full analysis JSON
            Map<String, Object> response = new HashMap<>();
            response.put("analysis_result_url", request.getAnalysisResultUrl());
            response.put("public_id", request.getAnalysisResultPublicId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error downloading analysis result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to download analysis result: " + e.getMessage()));
        }
    }

    private int estimateProcessingTime(double durationSeconds) {
        // Rough estimate: 2-4x the audio duration for processing
        return (int) Math.ceil(durationSeconds * 3);
    }

    private Map<String, Object> createHistoryItem(AnalysisRequest request) {
        Map<String, Object> item = new HashMap<>();
        item.put("request_id", request.getId());
        item.put("file_name", request.getFileName());
        item.put("status", request.getStatus());
        item.put("requested_at", request.getRequestedAt());
        item.put("duration_seconds", request.getAudioDurationSeconds());

        if (request.getStatus() == AnalysisRequest.AnalysisStatus.COMPLETED) {
            item.put("completed_at", request.getCompletedAt());
            item.put("quick_results", request.getQuickResults());
        } else if (request.getStatus() == AnalysisRequest.AnalysisStatus.FAILED) {
            item.put("error", request.getErrorMessage());
        }

        return item;
    }

    @PostMapping("/generate-random-topics")
    public ResponseEntity<?> generateRandomTopics() throws Exception {
        String output = aiService.generateRandomTopics();
        JsonNode topics = LlmUtils.extractJsonFromLlm(output);
        return ResponseEntity.ok(topics);
    }

    @PostMapping("/generate-speech")
    public ResponseEntity<?> generateSpeech(@RequestBody String topic) throws Exception {
        String output = aiService.generateSpeech(topic);
        JsonNode speech = LlmUtils.extractJsonFromLlm(output);
        return ResponseEntity.ok(speech);
    }

    @PostMapping("/generate-rephrasals")
    public ResponseEntity<?> generateRephrasals(@RequestBody String speech) throws Exception {
        String output = aiService.generateRephrasals(speech);
        JsonNode rephrasals = LlmUtils.extractJsonFromLlm(output);
        return ResponseEntity.ok(rephrasals);
    }

    @PostMapping("/generate-slow-fast-drill")
    public ResponseEntity<?> generateSlowFastDrill() throws Exception {
        String output = aiService.generateSlowFastDrill();
        JsonNode drill = LlmUtils.extractJsonFromLlm(output);
        return ResponseEntity.ok(drill);
    }

    /**
     * Delete analysis and all associated files
     */
    @DeleteMapping("/analysis/{requestId}")
    public ResponseEntity<?> deleteAnalysis(@PathVariable String requestId) {
        try {
            String userId = userService.getCurrentUserId();
            log.info("Delete analysis request for user: {}, requestId: {}", userId, requestId);

            // Find the analysis request and verify ownership
            Optional<AnalysisRequest> optionalRequest = analysisRequestRepository.findByIdAndUserId(requestId, userId);
            if (optionalRequest.isEmpty()) {
                log.warn("Analysis request not found or user not authorized: {}", requestId);
                return ResponseEntity.notFound().build();
            }

            AnalysisRequest request = optionalRequest.get();

            // Step 1: Delete audio from Cloudinary
            if (request.getAudioPublicId() != null) {
                try {
                    cloudinaryService.deleteFile(request.getAudioPublicId(), "video"); // Audio is stored as video type
                    log.info("Audio deleted from Cloudinary: {}", request.getAudioPublicId());
                } catch (IOException e) {
                    log.error("Error deleting audio from Cloudinary: {}", e.getMessage());
                    // Continue with deletion even if audio deletion fails
                }
            }

            // Step 2: Fetch and parse analysis result JSON to get chart URLs
            if (request.getAnalysisResultUrl() != null) {
                try {
                    String analysisJson = cloudinaryService.fetchFileContent(request.getAnalysisResultUrl());
                    JsonNode analysisNode = objectMapper.readTree(analysisJson);

                    // Extract and delete chart URLs
                    deleteChartFromAnalysis(analysisNode, "speechRate");
                    deleteChartFromAnalysis(analysisNode, "intonation");

                    log.info("Chart files deleted from analysis result");
                } catch (Exception e) {
                    log.error("Error processing analysis result JSON: {}", e.getMessage());
                    // Continue with deletion even if chart deletion fails
                }

                // Step 3: Delete analysis result JSON file from Cloudinary
                if (request.getAnalysisResultPublicId() != null) {
                    try {
                        cloudinaryService.deleteFile(request.getAnalysisResultPublicId(), "raw");
                        log.info("Analysis result JSON deleted from Cloudinary: {}", request.getAnalysisResultPublicId());
                    } catch (IOException e) {
                        log.error("Error deleting analysis result JSON from Cloudinary: {}", e.getMessage());
                        // Continue with deletion even if JSON deletion fails
                    }
                }
            }

            // Step 4: Delete the document from MongoDB
            analysisRequestRepository.delete(request);
            log.info("Analysis request deleted from database: {}", requestId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Analysis deleted successfully");
            response.put("request_id", requestId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error deleting analysis", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete analysis: " + e.getMessage()));
        }
    }

    /**
     * Helper method to extract and delete chart URLs from analysis JSON
     */
    private void deleteChartFromAnalysis(JsonNode analysisNode, String chartType) {
        try {
            JsonNode chartNode = analysisNode.get(chartType);
            if (chartNode != null && chartNode.has("chartUrl")) {
                String chartUrl = chartNode.get("chartUrl").asText();
                if (chartUrl != null && !chartUrl.isEmpty()) {
                    String chartPublicId = cloudinaryService.extractPublicIdFromUrl(chartUrl);
                    if (chartPublicId != null) {
                        cloudinaryService.deleteFile(chartPublicId, "image");
                        log.info("Chart deleted from Cloudinary - type: {}, publicId: {}", chartType, chartPublicId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error deleting chart for type: {}", chartType, e);
        }
    }
}
