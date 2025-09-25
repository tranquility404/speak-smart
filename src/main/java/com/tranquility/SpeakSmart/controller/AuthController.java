package com.tranquility.SpeakSmart.controller;

import com.tranquility.SpeakSmart.model.LoginRequest;
import com.tranquility.SpeakSmart.model.RegisterRequest;
import com.tranquility.SpeakSmart.service.CustomUserDetailsService;
import com.tranquility.SpeakSmart.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String googleRedirectUri;

    @Value("${spring.security.oauth2.client.registration.github.client-id}")
    private String githubClientId;

    @Value("${spring.security.oauth2.client.registration.github.client-secret}")
    private String githubClientSecret;

    @Value("${spring.security.oauth2.client.registration.github.redirect-uri}")
    private String githubRedirectUri;

    @Value("${spring.security.oauth2.client.registration.linkedin.client-id}")
    private String linkedinClientId;

    @Value("${spring.security.oauth2.client.registration.linkedin.client-secret}")
    private String linkedinClientSecret;

    @Value("${spring.security.oauth2.client.registration.linkedin.redirect-uri}")
    private String linkedinRedirectUri;

    private RestTemplate restTemplate;
    private final PasswordEncoder passwordEncoder;

    private final UserService userService;
    private final CustomUserDetailsService userDetailsService;

    public AuthController(PasswordEncoder passwordEncoder, UserService userService, CustomUserDetailsService userDetailsService) {
        this.restTemplate = new RestTemplate();
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.userDetailsService = userDetailsService;
    }

    @GetMapping("/health-check")
    public ResponseEntity<?> greet() {
        return ResponseEntity.ok("Healthy Service");
    }

    private String getToken(String code, String clientId, String clientSecret, String redirectUri, String tokenEndpoint, String tokenName) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenEndpoint, request, Map.class);
        return (String) tokenResponse.getBody().get(tokenName);
    }

    @PostMapping("/google/callback")
    public ResponseEntity<?> handleGoogleCallback(@RequestParam String code) {
        try {
            String idToken = getToken(code, googleClientId, googleClientSecret, googleRedirectUri,
                    "https://oauth2.googleapis.com/token",
                    "id_token");

            String userInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            ResponseEntity<Map> userInfoResponse = restTemplate.getForEntity(userInfoUrl, Map.class);
            if (userInfoResponse.getStatusCode() == HttpStatus.OK) {
                String jwt = userService.registerWithGoogle(userInfoResponse);
                return ResponseEntity.ok(jwt);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Exception occurred while handleGoogleCallback ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/github/callback")
    public ResponseEntity<?> handleGithubCallback(@RequestParam String code) {
        try {
            String accessToken = getToken(code, githubClientId, githubClientSecret, githubRedirectUri,
                    "https://github.com/login/oauth/access_token",
                    "access_token");

            String userInfoUrl = "https://api.github.com/user";
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.setBearerAuth(accessToken);
            HttpEntity<Void> userRequest = new HttpEntity<>(userHeaders);

            ResponseEntity<Map> userInfoResponse = restTemplate.exchange(userInfoUrl, HttpMethod.GET, userRequest, Map.class);

            if (userInfoResponse.getStatusCode() == HttpStatus.OK) {
                String jwt = userService.registerWithGithub(userInfoResponse, userRequest, restTemplate);
                return ResponseEntity.ok(jwt);
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        } catch (Exception e) {
            log.error("Exception occurred while handling Github Callback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/linkedin/callback")
    public ResponseEntity<?> handleLinkedInCallback(@RequestParam String code) {
        try {
            String accessToken = getToken(code, linkedinClientId, linkedinClientSecret, linkedinRedirectUri,
                    "https://www.linkedin.com/oauth/v2/accessToken",
                    "access_token");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("X-Restli-Protocol-Version", "2.0.0"); // required for v2 API
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            String userInfoUrl = "https://api.linkedin.com/v2/userinfo";
            ResponseEntity<Map> userInfoResponse = restTemplate.exchange(userInfoUrl, HttpMethod.GET, requestEntity, Map.class);
            if (userInfoResponse.getStatusCode() == HttpStatus.OK) {
                String jwt = userService.registerWithLinkedin(userInfoResponse);
                return ResponseEntity.ok(jwt);
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        } catch (Exception e) {
            log.error("Exception occurred while handling LinkedIn Callback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            String jwt = userService.registerWitEmail(request);
            return ResponseEntity.ok(jwt);
        } catch (RuntimeException e) {
            log.error("Exception occurred while registering user via email ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            String jwt = userService.login(request);
            return ResponseEntity.ok(jwt);
        } catch (RuntimeException e) {
            log.error("Exception occurred while creating jwt token ", e);
            return new ResponseEntity<>("Incorrect Username or Password", HttpStatus.BAD_REQUEST);
        }
    }
}

