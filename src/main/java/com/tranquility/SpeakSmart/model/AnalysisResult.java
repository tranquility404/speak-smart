package com.tranquility.SpeakSmart.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AnalysisResult {

    private String requestId;
    private String userId;
    private String audioUrl;
    private LocalDateTime analyzedAt;
    private ProcessingMetrics processingMetrics;

    // Core Analysis Results
    private AudioMetadata audioMetadata;
    private SpeechRateAnalysis speechRate;
    private IntonationAnalysis intonation;
    private EnergyAnalysis energy;
    private PauseAnalysis pauses;
    private double overallScore;
    private VocabAnalysis vocabAnalysis;
//    private List<Suggestion> suggestions;

    // Transcription and AI Analysis
    private TranscriptionResult transcription;
//    private Map<String, Object> aiAnalysis;  // LLM-generated insights

    @Data
    public static class ProcessingMetrics {

        private long totalProcessingTimeMs;
        private long audioLoadTimeMs;
        private long transcriptionTimeMs;
        private long analysisTimeMs;
        private long memoryUsedMb;
    }

    @Data
    public static class AudioMetadata {
        private double durationSeconds;
        private int sampleRate;
        private int channels;
        private String format;
        private long fileSizeBytes;
    }

    @Data
    public static class SpeechSegment {
        private double start;
        private double end;
        private int startIndex;
        private int endIndex;
        private double speechRate;
    }

    @Data
    public static class TranscriptionResult {
        private String fullText;
        private String language;
        private int noOfWords;
    }

    @Data
    public static class SpeechRateAnalysis {
        private double avgSpeechRate;
        private double minWpm;
        private double maxWpm;
        private double standardDeviation;
        private List<SpeechSegment> segments;
        private SpeechSegment slowestSegment;
        private SpeechSegment fastestSegment;
        private String chartUrl;  // URL to chart image in Cloudinary
        private double score;  // 0-100
        private String feedback;
        private String category;
    }

    @Data
    public static class IntonationAnalysis {
        private double averagePitch;
        private double minPitch;
        private double maxPitch;
        private double pitchRange;
        private double pitchVariation;
        private double pitchVariationScore;
        private String chartUrl;
        private double score;
        private String feedback;
        private String category;
    }

    @Data
    public static class EnergyAnalysis {
        private double averageEnergy;
        private double minEnergy;
        private double maxEnergy;
        private double energyVariation;
//        private String chartUrl;
        private double score;
        private String feedback;
        private String category;
    }

    @Data
    public static class PauseAnalysis {
        private int totalPauses;
        private double totalPauseDuration;
        private double averagePauseDuration;
        private PauseSegment longestPause;
        private double score;
        private String feedback;
        private String category;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PauseSegment {
        private double startTime;
        private double endTime;
        private double duration;
    }

    @Data
    public static class Suggestion {

        private String category;
        private String title;
        private String description;
        private String actionable;
        private int priority; // 1-5
    }
}
