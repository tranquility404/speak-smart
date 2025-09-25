package com.tranquility.SpeakSmart.controller;

import com.tranquility.SpeakSmart.dto.UpdateUserRequest;
import com.tranquility.SpeakSmart.service.UserService;
import com.tranquility.SpeakSmart.service.UserProfilePictureService;
import com.tranquility.SpeakSmart.util.AuthUtils;
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
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @Autowired
    private UserProfilePictureService profilePictureService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/auth-status")
    public ResponseEntity<?> authStatus() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> getUser() {
        try {
            return ResponseEntity.ok(userService.getUser(AuthUtils.getUsername()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/profile-picture")
    public ResponseEntity<?> getProfilePicture() {
        try {
            return ResponseEntity.ok(userService.getProfilePicCloudUrl());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/profile-picture")
    public ResponseEntity<?> updateProfilePicture(@RequestParam("file") MultipartFile profilePic) {
        if (profilePic.isEmpty()) {
            return ResponseEntity.badRequest().body("Profile picture file is required");
        }

        try {
            // Validate file type
            String contentType = profilePic.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body("Only image files are allowed for profile pictures");
            }

            // Get current user ID
            String userId = userService.getCurrentUserId();

            // Get current profile picture URL to extract public ID for deletion
            String oldProfilePicUrl = null;
            try {
                oldProfilePicUrl = userService.getProfilePicCloudUrl();
            } catch (RuntimeException e) {
                // User might not have a profile picture yet, that's okay
                log.info("User doesn't have an existing profile picture");
            }

            // Extract public ID from old URL (if it exists and is from Cloudinary)
            String oldPublicId = extractPublicIdFromCloudinaryUrl(oldProfilePicUrl);

            // Upload new profile picture
            String newProfilePicUrl = profilePictureService.updateProfilePicture(profilePic, userId, oldPublicId);

            // Update user record
            userService.updateProfilePicCloudUrl(newProfilePicUrl);

            // Return response
            Map<String, String> response = new HashMap<>();
            response.put("profile_picture_url", newProfilePicUrl);
            response.put("message", "Profile picture updated successfully");

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error uploading profile picture", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload profile picture: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("Error updating profile picture", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    /**
     * Extract public ID from Cloudinary URL Example:
     * https://res.cloudinary.com/demo/image/upload/v1234567890/profiles/user_123/sample.jpg
     * Returns: profiles/user_123/sample
     */
    private String extractPublicIdFromCloudinaryUrl(String cloudinaryUrl) {
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

    @PostMapping("/info")
    public ResponseEntity<?> updateUserDetails(@RequestBody UpdateUserRequest request) {
        try {
            userService.updateUser(request);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
