package com.tranquility.SpeakSmart.service;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.tranquility.SpeakSmart.model.AnalysisRequest;
import com.tranquility.SpeakSmart.model.AnalysisResult;
import com.tranquility.SpeakSmart.model.VocabAnalysis;
import com.tranquility.SpeakSmart.util.LlmUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.XYChart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
public class SpeechAnalysisService {

    @Autowired
    private AIService aiService;

    @Autowired
    private ChartGenerationService chartGenerationService;

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 1024;
    private static final int OVERLAP = 512;

    /*public static void main(String[] args) throws Exception {
        String fileName = "aman.wav";
        String filePath = System.getProperty("user.dir") + "/audio/" + fileName;
        File file = new File(filePath);
        byte[] audioBytes = Files.readAllBytes(file.toPath());

        File inputTempFile = File.createTempFile("upload-", ".tmp");
        try (FileOutputStream fos = new FileOutputStream(inputTempFile)) {
            fos.write(audioBytes);
        }

        File outputFile = File.createTempFile("converted-", ".wav");
        String[] command = {"ffmpeg", "-y", "-i", inputTempFile.getAbsolutePath(), "-ar", "44100", "-ac", "1",
                outputFile.getAbsolutePath()
        };

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Audio conversion failed, exit code: " + exitCode);
        }

        byte[] fileBytes = Files.readAllBytes(outputFile.getAbsoluteFile().toPath());
        inputTempFile.delete();
        outputFile.delete();

        SpeechAnalysisService ob = new SpeechAnalysisService();

        AnalysisResult result = new AnalysisResult();
        AudioData audioData = ob.loadAudioData(fileBytes);
        result.setAudioMetadata(ob.createAudioMetadata(audioData, "audio/wav", 1000L));

//        AIService aiService1 = new AIService();
//
//        Map<String, Object> transcriptionResponse = aiService1.transcribe(fileBytes, file.getName());
//        ob.parseTranscriptionAndComputeSpeechRate(transcriptionResponse, result);

        AudioAnalysisResults analysisResults = ob.performSinglePassAnalysis(audioData);
        result.setIntonation(ob.calculateIntonationAnalysis(analysisResults.getPitchTimeSeries(), analysisResults.getPitchValues(), audioData.getDuration()));

        ob.analyzeEnergyAndPauses(analysisResults.getEnergyTimeSeries(), result);

//        result.setOverallScore(ob.calculateOverallScore(
//                result.getSpeechRate().getScore(),
//                result.getIntonation().getScore(),
//                result.getEnergy().getScore(),
//                result.getPauses().getScore()
//        ));

//        AnalysisResult.SpeechRateAnalysis speechRate = result.getSpeechRate();
//        System.out.println("\nSpeech Rate:-");
//        System.out.println("Avg: " + speechRate.getAvgSpeechRate());
//        System.out.println("Category: " + speechRate.getCategory());
//        System.out.println("Score: " + speechRate.getScore());
//        System.out.println("Fastest: " + speechRate.getMaxWpm() + "\t Slowest: " + speechRate.getMinWpm());
//        System.out.println("Feedback: " + speechRate.getFeedback());

        AnalysisResult.IntonationAnalysis intonation = result.getIntonation();
        System.out.println("\nIntonation:-");
        System.out.println("Avg: " + intonation.getAveragePitch());
        System.out.println("Category: " + intonation.getCategory());
        System.out.println("Score: " + intonation.getScore());
        System.out.println("Feedback: " + intonation.getFeedback());

        JFrame frame = new JFrame("XY Chart");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ChartGenerationService chartGenerationService1 = new ChartGenerationService();
        XYChart chart = chartGenerationService1.generateIntonationChart(intonation, analysisResults.getPitchTimeSeries());
        frame.add(new XChartPanel<>(chart));
        frame.pack();
        frame.setVisible(true);

        AnalysisResult.EnergyAnalysis energy = result.getEnergy();
        System.out.println("\nEnergy:-");
        System.out.println("Avg: " + energy.getAverageEnergy());
        System.out.println("Category: " + energy.getCategory());
        System.out.println("Score: " + energy.getScore());
        System.out.println("Feedback: " + energy.getFeedback());

        AnalysisResult.PauseAnalysis confidence = result.getPauses();
        System.out.println("\nConfidence:-");
        System.out.println("Avg Pause Duration: " + confidence.getAveragePauseDuration());
        System.out.println("Category: " + confidence.getCategory());
        System.out.println("Score: " + confidence.getScore());
        System.out.println("No of pauses: " + confidence.getTotalPauses() + "\t Longest Pause: " + confidence.getLongestPause());
        System.out.println("Feedback: " + confidence.getFeedback());

//        System.out.println("\nOverall Score: " + result.getOverallScore());
    }*/

    /**
     * Comprehensive single-pass audio analysis Processes audio once and
     * extracts all parameters efficiently
     */
    public AnalysisResult analyzeAudio(MultipartFile audioFile, AnalysisRequest request) throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("Starting comprehensive audio analysis for request: {}", request.getId());

        AnalysisResult result = new AnalysisResult();
        result.setRequestId(request.getId());
        result.setUserId(request.getUserId());
        result.setAudioUrl(request.getAudioUrl());
        result.setAnalyzedAt(LocalDateTime.now());

        try {
            // Step 1: Load and validate audio
            long audioLoadStart = System.currentTimeMillis();
            AudioData audioData = loadAudioData(audioFile.getBytes());
            long audioLoadTime = System.currentTimeMillis() - audioLoadStart;

            // Step 2: Get transcription (parallel to audio processing)
            long transcriptionStart = System.currentTimeMillis();
            Map<String, Object> transcriptionResponse = aiService.transcribe(audioFile.getBytes(), audioFile.getOriginalFilename());
            parseTranscriptionAndComputeSpeechRate(transcriptionResponse, result);

            long transcriptionTime = System.currentTimeMillis() - transcriptionStart;

            // Step 3: Single-pass audio analysis
            long analysisStart = System.currentTimeMillis();
            AudioAnalysisResults analysisResults = performSinglePassAnalysis(audioData);
            long analysisTime = System.currentTimeMillis() - analysisStart;

            // Step 4: Calculate comprehensive metrics
            result.setAudioMetadata(createAudioMetadata(audioData, audioFile.getContentType(), audioFile.getSize()));
            result.setIntonation(calculateIntonationAnalysis(analysisResults.getPitchTimeSeries(), analysisResults.getPitchValues(), audioData.getDuration()));
            analyzeEnergyAndPauses(analysisResults.getEnergyTimeSeries(), result);

            // Step 5: Calculate overall score and suggestions
            result.setOverallScore(calculateOverallScore(
                    result.getSpeechRate().getScore(),
                    result.getIntonation().getScore(),
                    result.getEnergy().getScore(),
                    result.getPauses().getScore()
            ));
//            result.setSuggestions(generateSuggestions(result));

            // Step 6: AI-powered analysis (async if possible)
//            result.setAiAnalysis(getAIInsights(transcription.getFullText()));

            // Step 7: Generate charts for visual analysis
            generateAnalysisCharts(result, analysisResults);
            getVocabAnalysis(result);

            // Step 8: Create processing metrics
            long totalTime = System.currentTimeMillis() - startTime;
            AnalysisResult.ProcessingMetrics metrics = new AnalysisResult.ProcessingMetrics();
            metrics.setTotalProcessingTimeMs(totalTime);
            metrics.setAudioLoadTimeMs(audioLoadTime);
            metrics.setTranscriptionTimeMs(transcriptionTime);
            metrics.setAnalysisTimeMs(analysisTime);
            metrics.setMemoryUsedMb(getMemoryUsage());
            result.setProcessingMetrics(metrics);

            log.info("Analysis completed in {}ms for request: {}", totalTime, request.getId());
            return result;

        } catch (Exception e) {
            log.error("Error during audio analysis for request: {}", request.getId(), e);
            throw e;
        }
    }

    public VocabAnalysis getVocabAnalysis(AnalysisResult result) throws Exception {
        String data = aiService.getLlmAnalysis(result.getTranscription().getFullText());
        JsonNode jsonData = LlmUtils.extractJsonFromLlm(data);

        VocabAnalysis vocabAnalysis = new VocabAnalysis(jsonData);
        System.out.println("vocab analysis: " + vocabAnalysis);     // Debug log

        result.setVocabAnalysis(vocabAnalysis);
        return vocabAnalysis;
    }

    /**
     * Optimized audio data loading with proper resource management
     */
    private AudioData loadAudioData(byte[] audioBytes) {
        AudioData audioData = new AudioData();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
             AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bais)) {
            long frames = audioInputStream.getFrameLength();
            float frameRate = audioInputStream.getFormat().getFrameRate();

            double durationSeconds = (double) frames / frameRate;
            audioData.setSampleRate((int) audioInputStream.getFormat().getSampleRate());

            // Convert to mono if needed and normalize sample rate
            List<Float> samples = new ArrayList<>();

            // Simple 16-bit PCM conversion (can be enhanced based on actual audio format)
            for (int i = 0; i < audioBytes.length - 1; i += 2) {
                short sample = (short) ((audioBytes[i + 1] << 8) | (audioBytes[i] & 0xff));
                samples.add(sample / 32768.0f);
            }

            audioData.setSamples(samples.stream().mapToDouble(Float::doubleValue).toArray());
            audioData.setDuration(durationSeconds);
        } catch (IOException|UnsupportedAudioFileException e) {
            log.error("Error occurred while loading audio: %s".formatted(e.getMessage()));
        }
        return audioData;
    }

    /**
     * Single-pass analysis that extracts all audio features in one go
     */
    private AudioAnalysisResults performSinglePassAnalysis(AudioData audioData) throws UnsupportedAudioFileException {
        AudioAnalysisResults results = new AudioAnalysisResults();

        Queue<Double> pitchValues = new ConcurrentLinkedQueue<>();
        Queue<Double> energyValues = new ConcurrentLinkedQueue<>();
        Queue<TimeValuePair> pitchTimeSeries = new ConcurrentLinkedQueue<>();
        Queue<TimeValuePair> energyTimeSeries = new ConcurrentLinkedQueue<>();

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromFloatArray(
                floatArrayFromDouble(audioData.getSamples()),
                audioData.getSampleRate(),
                BUFFER_SIZE,
                OVERLAP
        );

        // Pitch detection processor
        PitchProcessor pitchProcessor = new PitchProcessor(
                PitchProcessor.PitchEstimationAlgorithm.YIN,
                audioData.getSampleRate(),
                BUFFER_SIZE,
                (result, audioEvent) -> {
                    double timeStamp = audioEvent.getTimeStamp();
                    float pitch = result.getPitch();

                    if (pitch > 50 && pitch < 800) { // Filtering out noise or silence
                        pitchValues.offer((double) pitch);
                        pitchTimeSeries.offer(new TimeValuePair(timeStamp, pitch));
                    }
                }
        );

        // Energy detection processor
        AudioProcessor energyProcessor = new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                float[] buffer = audioEvent.getFloatBuffer();
                double timeStamp = audioEvent.getTimeStamp();

                // Calculate RMS energy
                double sum = 0;
                for (float sample : buffer) {
                    sum += sample * sample;
                }
                double rms = Math.sqrt(sum / buffer.length);

                energyValues.offer(rms);
                energyTimeSeries.offer(new TimeValuePair(timeStamp, rms));

                return true;
            }

            @Override
            public void processingFinished() {
            }
        };

        dispatcher.addAudioProcessor(pitchProcessor);
        dispatcher.addAudioProcessor(energyProcessor);
        dispatcher.run();

        // Convert queues to arrays for analysis
        results.setPitchValues(pitchValues.stream().filter(p -> !Double.isNaN(p) && p > 0).mapToDouble(Double::doubleValue).toArray());
        results.setEnergyValues(energyValues.stream().mapToDouble(Double::doubleValue).toArray());
        results.setPitchTimeSeries(new ArrayList<>(pitchTimeSeries));
        results.setEnergyTimeSeries(new ArrayList<>(energyTimeSeries));

        return results;
    }

    // 1. Extracting details from transcript. 2. Creating segment list for chart later. 3. Calculating SpeechRate. 4. Also finding slowest & fastest part of speech suggesting nervous parts.
    private void parseTranscriptionAndComputeSpeechRate(Map<String, Object> response, AnalysisResult analysisResult) {
        AnalysisResult.TranscriptionResult transcription = new AnalysisResult.TranscriptionResult();
        AnalysisResult.SpeechRateAnalysis speechRate = new AnalysisResult.SpeechRateAnalysis();

        String fullText = (String) response.get("text");
        transcription.setFullText(fullText != null ? fullText : "");
        transcription.setLanguage((String) response.get("language"));

        List<AnalysisResult.SpeechSegment> segments = new ArrayList<>();
        double minWpm = Double.MAX_VALUE;
        double maxWpm = Double.MIN_VALUE;
        AnalysisResult.SpeechSegment slowest = null;
        AnalysisResult.SpeechSegment fastest = null;

        if (response.containsKey("segments") && response.get("segments") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> segmentMaps = (List<Map<String, Object>>) response.get("segments");

            int startIndex = 0;
            for (Map<String, Object> segmentData : segmentMaps) {
                AnalysisResult.SpeechSegment segment = new AnalysisResult.SpeechSegment();
                segment.setStart(((Number) segmentData.getOrDefault("start", 0.0)).doubleValue());
                segment.setEnd(((Number) segmentData.getOrDefault("end", 1.0)).doubleValue());

                String text = (String) segmentData.getOrDefault("text", "");
                segment.setStartIndex(startIndex);
                int newEndIndex = startIndex + text.length();
                segment.setEndIndex(newEndIndex);
                startIndex = newEndIndex;

                double duration = segment.getEnd() - segment.getStart();
                int wordCount = text.split("\\s+").length;
                double wpm = ((double) wordCount / duration) * 60.0;
                minWpm = Math.min(minWpm, wpm);
                maxWpm = Math.max(maxWpm, wpm);

                if (slowest == null || wpm < slowest.getSpeechRate())
                    slowest = segment;
                if (fastest == null || wpm > fastest.getSpeechRate())
                    fastest = segment;

                segment.setSpeechRate(wpm);
                segments.add(segment);
            }
        } else if (fullText != null && !fullText.isEmpty()) {
            // Fallback: create a single segment for the entire text
            AnalysisResult.SpeechSegment segment = new AnalysisResult.SpeechSegment();
            segment.setStart(0.0);
            double duration = ((Number) response.getOrDefault("duration", 0.0)).doubleValue();
            segment.setEnd(duration); // entire durataion
            segment.setStartIndex(0);
            segment.setEndIndex(fullText.length());
            segment.setSpeechRate((fullText.split("\\s+").length / duration) * 60.0);
            segments.add(segment);
        }

        if (!segments.isEmpty()) {
            double avgSpeechRate = segments.stream().mapToDouble(AnalysisResult.SpeechSegment::getSpeechRate).average().orElse(0.8);
            speechRate.setAvgSpeechRate(avgSpeechRate);
            double variance = segments.stream()
                    .mapToDouble(s -> Math.pow(s.getSpeechRate() - speechRate.getAvgSpeechRate(), 2))
                    .average().orElse(0);
            speechRate.setStandardDeviation(Math.sqrt(variance));

            // Calculate score and feedback
            speechRate.setCategory(calculateSpeechRateCategory(avgSpeechRate));
            speechRate.setScore(calculateSpeechRateScore(avgSpeechRate));
            speechRate.setFeedback(generateSpeechRateFeedback(avgSpeechRate));
        } else
            speechRate.setAvgSpeechRate(0);

        speechRate.setMinWpm(minWpm);
        speechRate.setMaxWpm(maxWpm);
        speechRate.setSlowestSegment(slowest);
        speechRate.setFastestSegment(fastest);
        speechRate.setSegments(segments);

        analysisResult.setTranscription(transcription);
        analysisResult.setSpeechRate(speechRate);
    }

    private AnalysisResult.IntonationAnalysis calculateIntonationAnalysis(List<TimeValuePair> pitchTimeSeries, double[] pitchValues, double duration) {
        AnalysisResult.IntonationAnalysis analysis = new AnalysisResult.IntonationAnalysis();
        if (pitchTimeSeries.isEmpty()) return analysis;

        double pitchSum = 0;
        double pitchMin = Double.MAX_VALUE;
        double pitchMax = Double.MIN_VALUE;
        double pitchM2 = 0; // for variance (Welford's method)
        int count = 0;

        for (TimeValuePair tvp : pitchTimeSeries) {
            double pitch = tvp.getValue();
            count++;

            // Update sum, min, max
            pitchSum += pitch;
            pitchMin = Math.min(pitchMin, pitch);
            pitchMax = Math.max(pitchMax, pitch);

            // Welford's online variance
            double delta = pitch - (pitchSum / count);
            pitchM2 += delta * (pitch - (pitchSum / count));
        }

        double avgPitch = pitchSum / count;
        double stdDev = count > 1 ? Math.sqrt(pitchM2 / (count - 1)) : 0;
        double pitchVariation = avgPitch != 0 ? stdDev / avgPitch : 0;

        // Fill analysis object
        analysis.setAveragePitch(avgPitch);
        analysis.setMinPitch(pitchMin);
        analysis.setMaxPitch(pitchMax);
        analysis.setPitchRange(pitchMax - pitchMin);
        analysis.setPitchVariation(pitchVariation);

        // Score & feedback
        analysis.setPitchVariationScore(calculatePitchVariationScore(pitchValues, analysis.getAveragePitch(), stdDev));
        analysis.setCategory(calculateIntonationCategory(analysis.getPitchVariationScore()));
        analysis.setScore(analysis.getPitchVariationScore());
        analysis.setFeedback(generateIntonationFeedback(analysis.getPitchVariationScore()));
        return analysis;
    }

//    1. Computes Energy. 2. Analyzes Awkward Pauses (silences, low energy).
    private void analyzeEnergyAndPauses(List<TimeValuePair> energyTimeSeries, AnalysisResult result) {
        int totalPauses = 0;
        int n = energyTimeSeries.size();

        // Energy stats
        double sumEnergy = 0;
        double energyMin = Double.MAX_VALUE;
        double energyMax = Double.MIN_VALUE;
        double m2 = 0; // Welford's for variance
        int count = 0;

        // Pause tracking
        boolean inPause = false;
        double pauseStart = 0;
        double totalPauseDuration = 0;
        AnalysisResult.PauseSegment longestPause = new AnalysisResult.PauseSegment();

        for (var tvp: energyTimeSeries) {
            double energy = tvp.getValue();
            double time = tvp.getTime();

            count++;        // --- Energy stats ---
            sumEnergy += energy;
            energyMin = Math.min(energyMin, energy);
            energyMax = Math.max(energyMax, energy);

            double delta = energy - (sumEnergy / count);
            m2 += delta * (energy - (sumEnergy / count));

            if (energy < 0.01) {      // --- Pause detection ---
                if (!inPause) {
                    inPause = true;
                    pauseStart = time;
                }
            } else {
                if (inPause) {
                    inPause = false;
                    double duration = time - pauseStart;
                    if (duration >= 0.3) {
                        totalPauses++;
                        totalPauseDuration += duration;
                        longestPause = duration > longestPause.getDuration()? new AnalysisResult.PauseSegment(pauseStart, time, duration): longestPause;
                    }
                }
            }
        }

        // Handle if audio ends while in pause
        if (inPause) {
            double duration = energyTimeSeries.get(n - 1).getTime() - pauseStart;
            if (duration >= 0.3) {
                totalPauses++;
                totalPauseDuration += duration;
                longestPause = duration > longestPause.getDuration()? new AnalysisResult.PauseSegment(pauseStart, energyTimeSeries.get(n - 1).getTime(), duration): longestPause;
            }
        }

        // --- Fill EnergyAnalysis ---
        AnalysisResult.EnergyAnalysis energyAnalysis = new AnalysisResult.EnergyAnalysis();
        double avgEnergy = sumEnergy / count;
        double stdDev = count > 1 ? Math.sqrt(m2 / (count - 1)) : 0;
        energyAnalysis.setAverageEnergy(avgEnergy);
        energyAnalysis.setMinEnergy(energyMin);
        energyAnalysis.setMaxEnergy(energyMax);
        energyAnalysis.setEnergyVariation(avgEnergy != 0 ? stdDev / avgEnergy : 0);
        energyAnalysis.setCategory(calculateEnergyCategory(avgEnergy));
        energyAnalysis.setScore(calculateEnergyScore(avgEnergy));
        energyAnalysis.setFeedback(generateEnergyFeedback(avgEnergy));

        // --- Fill PauseAnalysis ---
        AnalysisResult.PauseAnalysis pauseAnalysis = new AnalysisResult.PauseAnalysis();
        pauseAnalysis.setTotalPauses(totalPauses);
        pauseAnalysis.setTotalPauseDuration(totalPauseDuration);
        if (totalPauses > 0) {
            pauseAnalysis.setAveragePauseDuration(totalPauseDuration / totalPauses);
            pauseAnalysis.setLongestPause(longestPause);
        }
        double pauseRate = totalPauses / result.getAudioMetadata().getDurationSeconds();
        pauseAnalysis.setCategory(calculatePauseCategory(pauseRate));
        pauseAnalysis.setScore(calculatePauseScore(pauseRate));
        pauseAnalysis.setFeedback(generatePauseFeedback(pauseRate));

        // --- Set results ---
        result.setEnergy(energyAnalysis);
        result.setPauses(pauseAnalysis);
    }

    private String calculateSpeechRateCategory(double avgSpeechRate) {
        if (avgSpeechRate < 100) return "very slow";
        if (avgSpeechRate < 130) return "slow";
        if (avgSpeechRate > 200) return "very fast";
        if (avgSpeechRate > 175) return "fast";
        return "good";
    }

    private double calculateSpeechRateScore(double avgSpeechRate) {
        String category = calculateSpeechRateCategory(avgSpeechRate);
        return switch (category) {
            case "good" -> 50 + (avgSpeechRate - 130) * (20.0 / 45);        // 130-175
            case "slow" -> 30 + (avgSpeechRate - 100) * (20.0 / 30);        // 100-130
            case "fast" -> 50 + (avgSpeechRate - 175) * (20.0 / 25);        // 175-200
            case "very slow" -> Math.max(0, 30 - (100 - avgSpeechRate) * (30.0 / 100));
            default -> Math.min(100, 70 + (avgSpeechRate - 200) * (30.0 / 100)); // very fast
        };
    }

    private String generateSpeechRateFeedback(double avgSpeechRate) {
        String category = calculateSpeechRateCategory(avgSpeechRate);
        return switch (category) {
            case "very slow" -> "Increase your pace to make the speech more engaging.";
            case "slow" -> "Speed up slightly to keep the speech more dynamic.";
            case "very fast" -> "Slow down to make the speech clearer and more effective.";
            case "fast" -> "Reduce the speed a bit for better impact.";
            default -> "Well-paced! Keep the flow consistent.";
        };
    }

    public static double calculatePitchVariationScore(double[] valid, double mean, double stdDev) {
        if (valid.length == 0) return 0.0; // no voiced frames

        double zScore = (stdDev - 40) / 40; // Z-score vs average expressive speakers
        double score;
        if (stdDev < 20)
            score = stdDev / 20 * 0.5;  // too monotone → penalize heavily   // max 0.5
        else if (stdDev > 80)
            score = 80 / stdDev * 0.5; // too erratic → penalize // drops towards 0
        else
            score = 1.0 / (1.0 + Math.abs(zScore)); // good variation → scale around z-score

        return Math.max(0.0, Math.min(1.0, score)) * 100;   // clamp 0–1
    }

    private String calculateIntonationCategory(double score) {
        if (score > 50) return "high";
        if (score >= 25) return "good";
        return "low";
    }

    private double calculateIntonationScore(double score) {
        String category = calculateIntonationCategory(score);
        return switch (category) {
            case "good" -> 50 + (score - 25) * 2;
            case "low" -> (score / 25) * 50;
            default -> 100 - (score - 50);
        };
    }

    private String generateIntonationFeedback(double score) {
        String category = calculateIntonationCategory(score);
        return switch (category) {
            case "high" -> "Tone is too exaggerated — reduce variation for clarity.";
            case "good" -> "Good pitch variation! The speaker uses dynamic pitch changes for engagement.";
            default -> "The speaker shows limited pitch variation. This might sound monotonous.";
        };
    }

    private String calculateEnergyCategory(double avgEnergy) {
        if (avgEnergy < 0.04) return "low";
        if (avgEnergy > 0.08) return "high";
        return "good";
    }

    private double calculateEnergyScore(double avgEnergy) {
        String category = calculateEnergyCategory(avgEnergy);
        return switch (category) {
            case "good" -> 70 + (avgEnergy - 0.04) * (30.0 / 0.04);
            case "low" -> 30 + (avgEnergy - 0.02) * (40.0 / 0.02);
            default -> Math.max(0, 70 - (avgEnergy - 0.08) * (40.0 / 0.02));
        };
    }

    private String generateEnergyFeedback(double avgEnergy) {
        String category = calculateEnergyCategory(avgEnergy);
        return switch (category) {
            case "low" -> "Increase your volume to project more confidence.";
            case "good" -> "Good energy level — sounds confident and clear.";
            default -> "Lower your volume slightly to avoid sounding harsh.";
        };
    }

    // Category
    private String calculatePauseCategory(double pauseRate) {
        if (pauseRate < 2) return "less pauses";
        if (pauseRate > 8) return "many pauses";
        return "excellent";
    }

    // Percent score
    private double calculatePauseScore(double pauseRate) {
        String category = calculatePauseCategory(pauseRate);
        return switch (category) {
            case "less pauses" -> 90 - (pauseRate * (40.0 / 2));
            case "excellent" -> 70 - (pauseRate - 2) * (20.0 / 6);
            default -> 50 - (pauseRate - 8) * (30.0 / 4); // many pauses
        };
    }

    // Feedback
    private String generatePauseFeedback(double pauseRate) {
        String category = calculatePauseCategory(pauseRate);
        return switch (category) {
            case "less pauses" -> "Minimal pauses convey strong confidence and authority.";
            case "excellent" -> "Well-balanced pauses — projecting confidence and clarity.";
            default -> "Excessive pauses may weaken confidence; aim for a smoother flow.";
        };
    }


    @Data
    private static class AudioData {
        private double[] samples;
        private int sampleRate;
        private double duration;
    }

    @Data
    private static class AudioAnalysisResults {
        private double[] pitchValues;
        private double[] energyValues;
        private List<TimeValuePair> pitchTimeSeries;
        private List<TimeValuePair> energyTimeSeries;
    }

    @Data
    @AllArgsConstructor
    public static class TimeValuePair {
        private double time;
        private double value;
    }

    private float[] floatArrayFromDouble(double[] doubleArray) {
        float[] floatArray = new float[doubleArray.length];
        for (int i = 0; i < doubleArray.length; i++) {
            floatArray[i] = (float) doubleArray[i];
        }
        return floatArray;
    }

    private long getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024; // MB
    }

    // Placeholder methods that would need full implementation
    private AnalysisResult.AudioMetadata createAudioMetadata(AudioData audioData, String contentType, long size) {
        AnalysisResult.AudioMetadata metadata = new AnalysisResult.AudioMetadata();
        metadata.setDurationSeconds(audioData.getDuration());
        metadata.setSampleRate(audioData.getSampleRate());
        metadata.setFormat(contentType);
        metadata.setFileSizeBytes(size);
        metadata.setChannels(1); // Assuming mono after processing
        return metadata;
    }

    public double calculateOverallScore(double speechRatePercent, double intonationPercent, double energyPercent, double pausesPercent) {
        double part = 0.25;
        double speechRateScore = part * speechRatePercent;
        double intonationScore = part * intonationPercent;
        double energyScore = part * energyPercent;
        double confidenceScore = part * pausesPercent;
        return speechRateScore + intonationScore + energyScore + confidenceScore;
    }

    /**
     * Generate charts for all analysis results
     */
    private void generateAnalysisCharts(AnalysisResult result, AudioAnalysisResults audioAnalysisResults) {
        try {
            log.info("Starting chart generation for analysis result");

            // Generate speech rate chart
            if (result.getSpeechRate() != null) {
                String speechRateChartUrl = chartGenerationService.generateSpeechRateChart(result.getSpeechRate());
                result.getSpeechRate().setChartUrl(speechRateChartUrl);
                log.debug("Speech rate chart generated: {}", speechRateChartUrl);
            }

            // Generate intonation chart
            if (result.getIntonation() != null) {
                XYChart chart = chartGenerationService.generateIntonationChart(result.getIntonation(), audioAnalysisResults.pitchTimeSeries);
                String intonationChartUrl = chartGenerationService.uploadChartToCloudinary(chart, "intonation_chart_" + System.currentTimeMillis());
                result.getIntonation().setChartUrl(intonationChartUrl);
                log.debug("Intonation chart generated: {}", intonationChartUrl);
            }

            // Generate energy chart
//            if (result.getEnergy() != null) {
//                String energyChartUrl = chartGenerationService.generateEnergyChart(result.getEnergy());
//                result.getEnergy().setChartUrl(energyChartUrl);
//                log.debug("Energy chart generated: {}", energyChartUrl);
//            }

            log.info("Chart generation completed successfully");

        } catch (Exception e) {
            log.error("Error generating analysis charts", e);
            // Don't fail the entire analysis if chart generation fails
        }
    }
}
