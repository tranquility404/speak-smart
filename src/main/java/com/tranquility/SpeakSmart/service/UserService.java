package com.tranquility.SpeakSmart.service;

import com.tranquility.SpeakSmart.dto.LeaderboardDto;
import com.tranquility.SpeakSmart.dto.UpdateUserRequest;
import com.tranquility.SpeakSmart.dto.UserDto;
import com.tranquility.SpeakSmart.model.*;
import com.tranquility.SpeakSmart.repository.AnalysisRequestRepository;
import com.tranquility.SpeakSmart.repository.UserRepository;
import com.tranquility.SpeakSmart.util.AuthUtils;
import com.tranquility.SpeakSmart.util.JWTUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class UserService {
    private MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    @Autowired
    private JWTUtil jwt;

    public UserService(MongoTemplate mongoTemplate, UserRepository userRepository, AnalysisRequestRepository analysisRequestRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, CustomUserDetailsService userDetailsService) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.analysisRequestRepository = analysisRequestRepository;
    }

    public String registerWithGoogle(ResponseEntity<Map> userInfoResponse) {
        Map<String, Object> userInfo = userInfoResponse.getBody();
        String email = (String) userInfo.get("email");

        UserDetails userDetails = null;
        try{
            userDetails = userDetailsService.loadUserByUsername(email);
        }catch (Exception e){
            String name = (String) userInfo.get("name");
            String picture = (String) userInfo.get("picture");
            User user = new User();
            user.setName(name);
            user.setEmail(email);
            user.setUsername(email);
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setProfilePicCloudUrl(picture);
            user.setRoles(new HashSet<>(Arrays.asList(Roles.USER.name())));
            userRepository.save(user);
        }
        return jwt.generateToken(email);
    }

    public String registerWithGithub(ResponseEntity<Map> userInfoResponse, HttpEntity<Void> userRequest, RestTemplate restTemplate) {
        Map<String, Object> userInfo = userInfoResponse.getBody();
        String email = (String) userInfo.get("email");             // Can be null if private
        // If email is null, fetch via separate endpoint
        if (email == null) {
            String emailsUrl = "https://api.github.com/user/emails";
            HttpEntity<Void> emailRequest = new HttpEntity<>(userRequest.getHeaders());
            ResponseEntity<List> emailsResponse = restTemplate.exchange(
                    emailsUrl,
                    HttpMethod.GET,
                    emailRequest,
                    List.class
            );

            if (emailsResponse.getStatusCode() == HttpStatus.OK && !emailsResponse.getBody().isEmpty()) {
                Map<String, Object> primaryEmail = (Map<String, Object>) emailsResponse.getBody().get(0);
                email = (String) primaryEmail.get("email");
            }
        }

        UserDetails userDetails = null;
        try{
            userDetails = userDetailsService.loadUserByUsername(email);
        }catch (Exception e){
            String username = (String) userInfo.get("login");          // GitHub username
            String name = (String) userInfo.get("name");               // Full name (can be null)
            String avatarUrl = (String) userInfo.get("avatar_url");    // Profile picture

            User user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setProfilePicCloudUrl(avatarUrl);
            user.setRoles(new HashSet<>(Arrays.asList(Roles.USER.name())));
            userRepository.save(user);
        }
        return jwt.generateToken(email);
    }

    public String registerWithLinkedin(ResponseEntity<Map> userInfoResponse) {
        Map<String, Object> userInfo = userInfoResponse.getBody();
        String firstName = (String) userInfo.get("given_name"); // OIDC standard
        String lastName = (String) userInfo.get("family_name");
        String fullName = firstName + " " + lastName;
        String email = (String) userInfo.get("email"); // OIDC standard
        String profilePicUrl = (String) userInfo.get("picture"); // OIDC standard

        if (email == null) throw  new RuntimeException("Email not available from LinkedIn");
        UserDetails userDetails = null;
        try {
            userDetails = userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            User user = new User();
            user.setName(fullName);
            user.setEmail(email);
            user.setUsername(email);
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setProfilePicCloudUrl(profilePicUrl);
            user.setRoles(new HashSet<>(Arrays.asList(Roles.USER.name())));
            userRepository.save(user);
        }

        return jwt.generateToken(email);
    }

    public String registerWitEmail(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setName(request.getName());
        user.setUsername(request.getEmail());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(Collections.singleton(Roles.USER.name()));

        userRepository.save(user);
        return jwt.generateToken(request.getEmail());
    }

    public void updateUser(UpdateUserRequest request) {
        Optional<User> optional = userRepository.findByEmail(Objects.requireNonNull(AuthUtils.getUsername()));
        if (optional.isEmpty()) throw new RuntimeException("User not found");
        User user = optional.get();
        if (request.getUsername() != null)
            user.setUsername(request.getUsername());
        userRepository.save(user);
    }

    public List<LeaderboardDto> getTopRankers() {
        return userRepository.findAllByOrderByPointsDescLastAnalysisDesc(PageRequest.of(0, 15));
    }

    public void updateProfilePicCloudUrl(String profilePicCloudUrl) {
        Query query = new Query();
        query.addCriteria(Criteria.where("email").is(AuthUtils.getUsername()));

        Update update = new Update();
        update.set("profilePicCloudUrl", profilePicCloudUrl);

        mongoTemplate.updateFirst(query, update, User.class);
    }

    public String login(LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found"));

        return jwt.generateToken(user.getEmail());
    }

    public User getUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    public User getCurrentUser() {
        return userRepository.findByEmail(AuthUtils.getUsername()).orElseThrow();
    }

    public void updateAnalysisPoints(String userid, Instant completed) {
        User user = userRepository.findById(userid).orElseThrow();
        int streak = 1;
        try {
            long days = ChronoUnit.DAYS.between(user.getLastAnalysis(), completed);
            if (days == 0)
                streak = user.getStreak();
            else if (days == 1)
                streak = user.getStreak() + 1;
        } catch (Exception e) {
            log.error("First analysis: %s".formatted(e.getMessage()));
        }

        Query query = new Query(Criteria.where("_id").is(userid));
        Update update = new Update()
                .inc("points", 10 * streak)
                .inc("analysisCount", 1)
                .set("streak", streak)
                .set("lastAnalysis", completed);

        mongoTemplate.updateFirst(query, update, User.class);
    }

    public UserDto getCurrentUserInfo() {
        Optional<User> optional = userRepository.findByEmail(AuthUtils.getUsername());
        if (optional.isEmpty()) throw new RuntimeException("User not found");
        return convertToDto(optional.get());
    }

    public String getCurrentUserId() {
        Optional<User> optional = userRepository.findByEmail(AuthUtils.getUsername());
        if (optional.isEmpty()) throw new RuntimeException("User not found");
        return optional.get().getId();
    }

    public String getProfilePicCloudUrl() {
        User user = userRepository.findUserByEmailWithProfilePicCloudUrl(AuthUtils.getUsername());
        if (user == null) throw new RuntimeException("User not found");
        return user.getProfilePicCloudUrl();
    }

    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setProfilePicCloudUrl(user.getProfilePicCloudUrl());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        return dto;
    }
}

