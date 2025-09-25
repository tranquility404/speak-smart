package com.tranquility.SpeakSmart.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private final Cloudinary cloudinary;

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
    public Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException {
        try {
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "resource_type", determineResourceType(file.getContentType()),
                    "folder", folder,
                    "unique_filename", true,
                    "overwrite", false
            );

            Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), uploadParams);
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
                    "public_id", fileName,       // sets filename in Cloudinary
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
    public Map<String, Object> uploadAudio(MultipartFile audioFile) throws IOException {
        return uploadFile(audioFile, "audio");
    }

    /**
     * Upload image file specifically
     */
    public Map<String, Object> uploadImage(MultipartFile imageFile) throws IOException {
        return uploadFile(imageFile, "images");
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
