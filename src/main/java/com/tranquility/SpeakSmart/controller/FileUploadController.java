package com.tranquility.SpeakSmart.controller;

import com.tranquility.SpeakSmart.service.CloudinaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    @Autowired
    private CloudinaryService cloudinaryService;

    @PostMapping("/upload-audio")
    public ResponseEntity<?> uploadAudio(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Audio file is required");
        }

        try {
            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("audio/")) {
                return ResponseEntity.badRequest().body("Only audio files are allowed");
            }

            // Upload to Cloudinary
            Map<String, Object> result = cloudinaryService.uploadAudio(file.getBytes(), contentType);

            // Return essential information
            Map<String, Object> response = new HashMap<>();
            response.put("url", result.get("secure_url"));
            response.put("public_id", result.get("public_id"));
            response.put("resource_type", result.get("resource_type"));
            response.put("format", result.get("format"));
            response.put("bytes", result.get("bytes"));
            response.put("duration", result.get("duration"));

            log.info("Audio file uploaded successfully: {}", result.get("secure_url"));
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error uploading audio file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload audio file: " + e.getMessage());
        }
    }

    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Image file is required");
        }

        try {
            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body("Only image files are allowed");
            }

            // Upload to Cloudinary
            Map<String, Object> result = cloudinaryService.uploadImage(file, contentType);

            // Return essential information
            Map<String, Object> response = new HashMap<>();
            response.put("url", result.get("secure_url"));
            response.put("public_id", result.get("public_id"));
            response.put("resource_type", result.get("resource_type"));
            response.put("format", result.get("format"));
            response.put("bytes", result.get("bytes"));
            response.put("width", result.get("width"));
            response.put("height", result.get("height"));

            log.info("Image file uploaded successfully: {}", result.get("secure_url"));
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error uploading image file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload image file: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{publicId}")
    public ResponseEntity<?> deleteFile(
            @PathVariable String publicId,
            @RequestParam(defaultValue = "image") String resourceType) {

        try {
            Map<String, Object> result = cloudinaryService.deleteFile(publicId, resourceType);
            log.info("File deleted successfully: {}", publicId);
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            log.error("Error deleting file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete file: " + e.getMessage());
        }
    }
}
