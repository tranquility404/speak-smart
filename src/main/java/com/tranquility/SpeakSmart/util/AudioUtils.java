package com.tranquility.SpeakSmart.util;

import com.tranquility.SpeakSmart.model.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

@Slf4j
public class AudioUtils {

    public static ValidationResult validateAudioFile(MultipartFile file) {
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

            result.setAudioFile(convertAudioToWav(file.getBytes()));
            // Try to get audio duration
            log.info("Attempting to read audio stream for duration calculation...");
            try {
                // Use BufferedInputStream to support mark/reset operations
                ByteArrayInputStream inputStream = new ByteArrayInputStream(result.getAudioFile());
                try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputStream)) {
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

    public static byte[] convertAudioToWav(byte[] audioBytes) throws IOException, InterruptedException {
        File inputTempFile = File.createTempFile("upload-", ".tmp");
        try (FileOutputStream fos = new FileOutputStream(inputTempFile)) {
            fos.write(audioBytes);
        }

        File outputFile = File.createTempFile("converted-", ".wav");
        String[] command = {"ffmpeg", "-y", "-i", inputTempFile.getAbsolutePath(), "-ar", "44100", "-ac", "1",
                outputFile.getAbsolutePath()
        };

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Audio conversion failed, exit code: " + exitCode);
        }

        byte[] fileBytes = Files.readAllBytes(outputFile.getAbsoluteFile().toPath());
        inputTempFile.delete();
        outputFile.delete();
        return fileBytes;
    }
}
