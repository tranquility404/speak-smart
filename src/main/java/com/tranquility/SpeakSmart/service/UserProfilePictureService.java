package com.tranquility.SpeakSmart.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Service to handle user profile picture uploads using Cloudinary instead of
 * Google Cloud Storage
 */
@Service
public class UserProfilePictureService {

    @Autowired
    private CloudinaryService cloudinaryService;

    /**
     * Upload user profile picture to Cloudinary
     *
     * @param profilePicture The profile picture file
     * @param userId User ID to create a unique folder structure
     * @return Secure URL of the uploaded image
     */
    public String uploadProfilePicture(MultipartFile profilePicture, String userId) throws IOException {
        // Create a folder structure like "profiles/user_123/"
        String folder = "profiles/user_" + userId;

        Map<String, Object> uploadResult = cloudinaryService.uploadFile(profilePicture, folder);
        return (String) uploadResult.get("secure_url");
    }

    /**
     * Update existing profile picture
     *
     * @param profilePicture New profile picture
     * @param userId User ID
     * @param oldPublicId Public ID of the old image to delete (optional)
     * @return New secure URL
     */
    public String updateProfilePicture(MultipartFile profilePicture, String userId, String oldPublicId) throws IOException {
        // Delete old image if public ID is provided
        if (oldPublicId != null && !oldPublicId.isEmpty()) {
            try {
                cloudinaryService.deleteFile(oldPublicId, "image");
            } catch (IOException e) {
                // Log the error but don't fail the upload
                System.err.println("Failed to delete old profile picture: " + e.getMessage());
            }
        }

        // Upload new image
        return uploadProfilePicture(profilePicture, userId);
    }

    /**
     * Delete profile picture
     *
     * @param publicId Public ID of the image to delete
     */
    public void deleteProfilePicture(String publicId) throws IOException {
        cloudinaryService.deleteFile(publicId, "image");
    }
}
