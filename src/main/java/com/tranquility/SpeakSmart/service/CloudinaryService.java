package com.tranquility.SpeakSmart.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private final Cloudinary cloudinary;
    private final RestTemplate restTemplate = new RestTemplate();

    public CloudinaryService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {

        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
    }

    /**
     * Upload audio file to Cloudinary
     *
     * @param file MultipartFile to upload
     * @param folder Folder to store the file in (e.g., "audio", "images")
     * @return Map containing upload result with secure_url and public_id
     */
    public Map<String, Object> uploadFile(byte[] file, String contentType, String folder) throws IOException {
        try {
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "resource_type", determineResourceType(contentType),
                    "folder", folder,
                    "unique_filename", true,
                    "overwrite", false
            );

            Map<String, Object> result = cloudinary.uploader().upload(file, uploadParams);
            log.info("File uploaded successfully to Cloudinary: {}", result.get("secure_url"));
            return result;

        } catch (IOException e) {
            log.error("Error uploading file to Cloudinary: {}", e.getMessage());
            throw e;
        }
    }

    public Map<String, Object> uploadFile(byte[] file, String fileName, String contentType, String folder) throws IOException {
        try {
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "resource_type", determineResourceType(contentType),
                    "folder", folder,
                    "unique_filename", true,
                    "public_id", fileName,
                    "overwrite", false
            );

            Map<String, Object> result = cloudinary.uploader().upload(file, uploadParams);
            log.info("File uploaded successfully to Cloudinary: {}", result.get("secure_url"));
            return result;

        } catch (IOException e) {
            log.error("Error uploading file to Cloudinary: {}", e.getMessage());
            throw e;
        }
    }

    public Map<String, Object> uploadFile(String content, String fileName, String folder, String contentType) throws IOException {
        try {
            byte[] file = content.getBytes(StandardCharsets.UTF_8);
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "resource_type", determineResourceType(contentType),
                    "folder", folder,
                    "unique_filename", true,
                    "public_id", fileName, // sets filename in Cloudinary
                    "overwrite", false
            );

            Map<String, Object> result = cloudinary.uploader().upload(file, uploadParams);
            log.info("File uploaded successfully to Cloudinary: {}", result.get("secure_url"));
            return result;

        } catch (IOException e) {
            log.error("Error uploading file to Cloudinary: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Upload audio file specifically
     */
    public Map<String, Object> uploadAudio(byte[] audioFile, String contentType) throws IOException {
        return uploadFile(audioFile, contentType, "audio");
    }

    /**
     * Upload image file specifically
     */
    public Map<String, Object> uploadImage(MultipartFile imageFile, String contentType) throws IOException {
        return uploadFile(imageFile.getBytes(), contentType, "images");
    }

    /**
     * Delete file from Cloudinary
     *
     * @param publicId The public ID of the file to delete
     * @param resourceType The resource type (image, video, raw)
     */
    public Map<String, Object> deleteFile(String publicId, String resourceType) throws IOException {
        try {
            Map<String, Object> result = cloudinary.uploader().destroy(publicId,
                    ObjectUtils.asMap("resource_type", resourceType));
            log.info("File deleted successfully from Cloudinary: {}", publicId);
            return result;
        } catch (IOException e) {
            log.error("Error deleting file from Cloudinary: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Fetch file content from Cloudinary URL
     *
     * @param url The Cloudinary URL to fetch content from
     * @return The content as a string
     * @throws IOException if there's an error fetching the content
     */
    public String fetchFileContent(String url) throws IOException {
        try {
            byte[] content = restTemplate.getForObject(url, byte[].class);
            if (content == null) {
                throw new IOException("No content received from URL: " + url);
            }
            return new String(content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error fetching file content from URL: {}", url);
            throw new IOException("Failed to fetch file content from: " + url, e);
        }
    }

    /**
     * Extract public ID from Cloudinary URL Example:
     * https://res.cloudinary.com/demo/raw/upload/v1234567890/analysis_results/analysis_result_123.json
     * Returns: analysis_results/analysis_result_123
     */
    public String extractPublicIdFromUrl(String cloudinaryUrl) {
        if (cloudinaryUrl == null || !cloudinaryUrl.contains("cloudinary.com")) {
            return null;
        }

        try {
            // Find the last occurrence of "/upload/" and extract everything after it
            int uploadIndex = cloudinaryUrl.lastIndexOf("/upload/");
            if (uploadIndex == -1) {
                return null;
            }

            String afterUpload = cloudinaryUrl.substring(uploadIndex + 8); // 8 = length of "/upload/"

            // Remove version if present (starts with v followed by digits)
            if (afterUpload.startsWith("v") && afterUpload.indexOf("/") > 1) {
                afterUpload = afterUpload.substring(afterUpload.indexOf("/") + 1);
            }

            // Remove file extension
            int dotIndex = afterUpload.lastIndexOf(".");
            if (dotIndex > 0) {
                afterUpload = afterUpload.substring(0, dotIndex);
            }

            return afterUpload;
        } catch (Exception e) {
            log.warn("Failed to extract public ID from URL: {}", cloudinaryUrl);
            return null;
        }
    }

    /**
     * Determine resource type based on file content type
     */
    private String determineResourceType(String contentType) {
        if (contentType == null) {
            return "raw";
        }

        if (contentType.startsWith("image/")) {
            return "image";
        } else if (contentType.startsWith("video/")) {
            return "video";
        } else if (contentType.startsWith("audio/")) {
            return "video"; // Cloudinary treats audio as video resource type
        } else {
            return "raw";
        }
    }
}
