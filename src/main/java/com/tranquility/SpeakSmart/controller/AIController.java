package com.tranquility.SpeakSmart.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tranquility.SpeakSmart.model.AnalysisRequest;
import com.tranquility.SpeakSmart.repository.AnalysisRequestRepository;
import com.tranquility.SpeakSmart.service.AIService;
import com.tranquility.SpeakSmart.service.AsyncAudioProcessingService;
import com.tranquility.SpeakSmart.service.CloudinaryService;
import com.tranquility.SpeakSmart.service.UserService;
import com.tranquility.SpeakSmart.util.LlmUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            ValidationResult validation = validateAudioFile(file);
            if (!validation.isValid()) {
                log.error("Audio file validation failed: {}", validation.getErrorMessage());
                return ResponseEntity.badRequest().body(Map.of("error", validation.getErrorMessage()));
            }

            log.info("Audio file validation successful");

            // Upload to Cloudinary immediately
            log.info("Starting Cloudinary upload...");
            long uploadStart = System.currentTimeMillis();
            Map<String, Object> uploadResult = cloudinaryService.uploadAudio(file);
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
                            request.getProcessingStartedAt(), LocalDateTime.now()).toMillis();
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
                        if (quickResults.containsKey("speech_rate_chart_url"))
                            chartUrls.put("speech_rate", (String) quickResults.get("speech_rate_chart_url"));
                        if (quickResults.containsKey("intonation_chart_url"))
                            chartUrls.put("intonation", (String) quickResults.get("intonation_chart_url"));
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

    // Helper methods
    private ValidationResult validateAudioFile(MultipartFile file) {
        ValidationResult result = new ValidationResult();

        log.info("Starting audio file validation for file: {}", file.getOriginalFilename());

        try {
            // Log file basic information
            log.info("File details - Name: {}, Size: {} bytes, ContentType: {}",
                    file.getOriginalFilename(), file.getSize(), file.getContentType());

            // Check if file is empty
            if (file.isEmpty()) {
                log.error("File validation failed: File is empty - {}", file.getOriginalFilename());
                result.setValid(false);
                result.setErrorMessage("Audio file is empty. Please select a valid audio file.");
                return result;
            }

            // Check file size (max 50MB)
            long maxSize = 50 * 1024 * 1024;
            if (file.getSize() > maxSize) {
                log.error("File validation failed: File too large - {} bytes (max: {} bytes)",
                        file.getSize(), maxSize);
                result.setValid(false);
                result.setErrorMessage("Audio file too large. Maximum size is 50MB.");
                return result;
            }
            log.info("File size validation passed: {} bytes", file.getSize());

            // Check content type
            String contentType = file.getContentType();
            if (contentType == null) {
                log.error("File validation failed: Content type is null for file - {}", file.getOriginalFilename());
                result.setValid(false);
                result.setErrorMessage("Unable to determine file type. Please ensure you're uploading an audio file.");
                return result;
            }

            if (!contentType.startsWith("audio/")) {
                log.error("File validation failed: Invalid content type - {} for file - {}",
                        contentType, file.getOriginalFilename());
                result.setValid(false);
                result.setErrorMessage("Invalid file type. Only audio files are allowed. Detected type: " + contentType);
                return result;
            }
            log.info("Content type validation passed: {}", contentType);

            // Try to get audio duration
            log.info("Attempting to read audio stream for duration calculation...");
            try {
                // Use BufferedInputStream to support mark/reset operations
                BufferedInputStream bufferedInputStream = new BufferedInputStream(file.getInputStream());

                try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bufferedInputStream)) {
                    log.info("Audio stream opened successfully");

                    long frames = audioInputStream.getFrameLength();
                    float frameRate = audioInputStream.getFormat().getFrameRate();

                    log.info("Audio format details - Frames: {}, Frame Rate: {}, Format: {}",
                            frames, frameRate, audioInputStream.getFormat());

                    if (frames == AudioSystem.NOT_SPECIFIED) {
                        log.warn("Frame length is not specified for audio file: {}", file.getOriginalFilename());
                        // For files where frame length is not available, we'll allow them through
                        result.setDurationSeconds(0); // Unknown duration
                    } else {
                        double durationSeconds = (double) frames / frameRate;
                        log.info("Calculated audio duration: {} seconds", durationSeconds);

                        // Check duration limits (max 10 minutes)
                        if (durationSeconds > 600) {
                            log.error("File validation failed: Audio too long - {} seconds (max: 600 seconds)",
                                    durationSeconds);
                            result.setValid(false);
                            result.setErrorMessage("Audio file too long. Maximum duration is 10 minutes.");
                            return result;
                        }

                        if (durationSeconds < 1) {
                            log.error("File validation failed: Audio too short - {} seconds (min: 1 second)",
                                    durationSeconds);
                            result.setValid(false);
                            result.setErrorMessage("Audio file too short. Minimum duration is 1 second.");
                            return result;
                        }

                        result.setDurationSeconds(durationSeconds);
                        log.info("Duration validation passed: {} seconds", durationSeconds);
                    }
                }
            } catch (UnsupportedAudioFileException audioEx) {
                log.error("Audio format validation failed for file: {} - {}",
                        file.getOriginalFilename(), audioEx.getMessage(), audioEx);
                result.setValid(false);
                result.setErrorMessage("Unsupported audio format. Please use common audio formats like MP3, WAV, or M4A. Error: " + audioEx.getMessage());
                return result;
            } catch (IOException audioIoEx) {
                log.error("IOException while reading audio stream for file: {} - {}",
                        file.getOriginalFilename(), audioIoEx.getMessage(), audioIoEx);
                result.setValid(false);
                result.setErrorMessage("Error reading audio file stream. File may be corrupted. Error: " + audioIoEx.getMessage());
                return result;
            }

            result.setValid(true);
            log.info("Audio file validation completed successfully for: {}", file.getOriginalFilename());
            return result;

        } catch (Exception e) {
            log.error("Unexpected error during file validation for file: {} - {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            result.setValid(false);
            result.setErrorMessage("Unexpected error during file validation: " + e.getMessage());
            return result;
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

    private static class ValidationResult {

        private boolean valid;
        private String errorMessage;
        private double durationSeconds;

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public double getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(double durationSeconds) {
            this.durationSeconds = durationSeconds;
        }
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
}
