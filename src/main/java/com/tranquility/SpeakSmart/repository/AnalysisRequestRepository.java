package com.tranquility.SpeakSmart.repository;

import com.tranquility.SpeakSmart.model.AnalysisRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisRequestRepository extends MongoRepository<AnalysisRequest, String> {

    List<AnalysisRequest> findByUserIdOrderByRequestedAtDesc(String userId);
    List<AnalysisRequest> findByUserIdOrderByCompletedAtDesc(String userId, Pageable pageable);

    List<AnalysisRequest> findByStatus(AnalysisRequest.AnalysisStatus status);

    @Query("{ 'status': ?0, 'retryCount': { $lt: ?1 } }")
    List<AnalysisRequest> findFailedRequestsForRetry(AnalysisRequest.AnalysisStatus status, int maxRetries);

    @Query("{ 'status': 'PROCESSING', 'processingStartedAt': { $lt: ?0 } }")
    List<AnalysisRequest> findStuckProcessingRequests(Instant cutoffTime);

    Optional<AnalysisRequest> findByIdAndUserId(String id, String userId);

    long countByUserIdAndStatus(String userId, AnalysisRequest.AnalysisStatus status);

    @Query("{ 'userId': ?0, 'status': { $in: ['COMPLETED'] }, 'completedAt': { $gte: ?1 } }")
    List<AnalysisRequest> findCompletedRequestsByUserSince(String userId, Instant since);
}
