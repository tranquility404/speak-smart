package com.tranquility.SpeakSmart.repository;

import com.tranquility.SpeakSmart.dto.LeaderboardDto;
import com.tranquility.SpeakSmart.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    List<LeaderboardDto> findAllByOrderByPointsDescLastAnalysisDesc(Pageable pageable);

    @Query(value = "{ 'email' : ?0 }", fields = "{ 'profilePicCloudUrl': 1 }")
    User findUserByEmailWithProfilePicCloudUrl(String email);
}

