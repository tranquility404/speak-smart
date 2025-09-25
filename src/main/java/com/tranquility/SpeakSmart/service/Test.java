//package com.tranquility.SpeakSmart.service;
//
//import be.tarsos.dsp.AudioDispatcher;
//import be.tarsos.dsp.AudioEvent;
//import be.tarsos.dsp.AudioProcessor;
//import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
//import be.tarsos.dsp.pitch.PitchProcessor;
//import com.tranquility.SpeakSmart.model.AnalysisResult;
//
//import javax.sound.sampled.UnsupportedAudioFileException;
//import java.util.ArrayList;
//import java.util.List;
//
//public class Test {
//
//    class Stats {
//        double sum = 0;
//        double min = Double.MAX_VALUE;
//        double max = Double.MIN_VALUE;
//        double m2 = 0; // for variance
//        int count = 0;
//    }
//
//    public AnalysisResult analyzeSinglePass(OptimizedAudioAnalysisService.AudioData audioData) throws UnsupportedAudioFileException {
//        AnalysisResult result = new AnalysisResult();
//        OptimizedAudioAnalysisService.Stats pitchStats = new OptimizedAudioAnalysisService.Stats(), energyStats = new OptimizedAudioAnalysisService.Stats();
//        List<AnalysisResult.PauseSegment> pauses = new ArrayList<>();
//
//        PauseState pauseState = new PauseState();
//        // Optional downsampled time series
//        List<OptimizedAudioAnalysisService.TimeValuePair> pitchTimeSeries = new ArrayList<>();
//        List<OptimizedAudioAnalysisService.TimeValuePair> energyTimeSeries = new ArrayList<>();
//        double timeSeriesStep = 0.1; // seconds
//        double[] lastSampleTime = {-timeSeriesStep, -timeSeriesStep};
//
//        // Setup audio dispatcher
//        AudioDispatcher dispatcher = AudioDispatcherFactory.fromFloatArray(
//                floatArrayFromDouble(audioData.getSamples()),
//                audioData.getSampleRate(),
//                BUFFER_SIZE,
//                OVERLAP
//        );
//
//        // Pitch processor
//        PitchProcessor pitchProcessor = new PitchProcessor(
//                PitchProcessor.PitchEstimationAlgorithm.YIN,
//                audioData.getSampleRate(),
//                BUFFER_SIZE,
//                (pitchResult, audioEvent) -> {
//                    double timeStamp = audioEvent.getTimeStamp();
//                    float pitch = pitchResult.getPitch();
//
//                    if (pitch >= 50 && pitch <= 800) {  // Filtering out noise or silence
//                        pitchStats.count++;
//                        double delta = pitch - (pitchStats.sum / pitchStats.count);
//                        pitchStats.sum += pitch;
//                        pitchStats.min = Math.min(pitchStats.min, pitch);
//                        pitchStats.max = Math.max(pitchStats.max, pitch);
//                        pitchStats.m2 += delta * (pitch - (pitchStats.sum / pitchStats.count));
//
//                        // Downsample time series
//                        if (timeStamp - lastSampleTime[0] >= timeSeriesStep) {
//                            pitchTimeSeries.add(new OptimizedAudioAnalysisService.TimeValuePair(timeStamp, (double)pitch));
//                            lastSampleTime[0] = timeStamp;
//                        }
//                    }
//                }
//        );
//
//        // Energy processor
//        AudioProcessor energyProcessor = new AudioProcessor() {
//            @Override
//            public boolean process(AudioEvent audioEvent) {
//                float[] buffer = audioEvent.getFloatBuffer();
//                double timeStamp = audioEvent.getTimeStamp();
//                double rms = calculateRMS(buffer);
//
//                energyStats.count++;
//                energyStats.sum += rms;
//                energyStats.min = Math.min(energyStats.min, rms);
//                energyStats.max = Math.max(energyStats.max, rms);
//
//                // Downsample time series
//                if (timeStamp - lastSampleTime[1] >= timeSeriesStep) {
//                    energyTimeSeries.add(new OptimizedAudioAnalysisService.TimeValuePair(timeStamp, rms));
//                    lastSampleTime[1] = timeStamp;
//                }
//                pauseState.handlePauseDetection(rms, timeStamp, pauses);
//                return true;
//            }
//
//            @Override
//            public void processingFinished() {}
//        };
//
//        dispatcher.addAudioProcessor(pitchProcessor);
//        dispatcher.addAudioProcessor(energyProcessor);
//        dispatcher.run();
//
//        // Compute final pitch stats
//        double avgPitch = pitchStats.count > 0 ? pitchStats.sum / pitchStats.count : 0;
//        double pitchStdDev = pitchStats.count > 1 ? Math.sqrt(pitchStats.m2 / (pitchStats.count - 1)) : 0;
//        double pitchVariation = avgPitch != 0 ? pitchStdDev / avgPitch : 0;
//
//        // Compute final energy stats
//        double avgEnergy = energyStats.count > 0 ? energyStats.sum / energyStats.count : 0;
//        double energyVariation = avgEnergy != 0 ? calculateEnergyStdDev(energyTimeSeries, avgEnergy) / avgEnergy : 0;
//
//        // Fill result object
//        AnalysisResult.IntonationAnalysis intonation = new AnalysisResult.IntonationAnalysis();
//        intonation.setAveragePitch(avgPitch);
//        intonation.setMinPitch(pitchStats.min);
//        intonation.setMaxPitch(pitchStats.max);
//        intonation.setPitchRange(pitchStats.max - pitchStats.min);
//        intonation.setPitchVariation(pitchVariation);
//        intonation.setSegments(createPitchSegments(pitchTimeSeries));
//        intonation.setScore(calculateIntonationScore(intonation));
//        intonation.setFeedback(generateIntonationFeedback(intonation));
//
//        AnalysisResult.EnergyAnalysis energy = new AnalysisResult.EnergyAnalysis();
//        energy.setAverageEnergy(avgEnergy);
//        energy.setMinEnergy(energyStats.min);
//        energy.setMaxEnergy(energyStats.max);
//        energy.setEnergyVariation(energyVariation);
//        energy.setSegments(createEnergySegments(energyTimeSeries));
//        energy.setScore(calculateEnergyScore(energy));
//        energy.setFeedback(generateEnergyFeedback(energy));
//
//        AnalysisResult.PauseAnalysis pauseAnalysis = new AnalysisResult.PauseAnalysis();
//        pauseAnalysis.setPauses(pauses);
//        pauseAnalysis.setTotalPauses(pauses.size());
//
//        double totalPauseDuration = pauses.stream().mapToDouble(AnalysisResult.PauseSegment::getDuration).sum();
//        pauseAnalysis.setTotalPauseDuration(totalPauseDuration);
//        if (!pauses.isEmpty()) {
//            pauseAnalysis.setAveragePauseDuration(totalPauseDuration / pauses.size());
//            pauseAnalysis.setLongestPause(pauses.stream().mapToDouble(AnalysisResult.PauseSegment::getDuration).max().orElse(0));
//        }
//        pauseAnalysis.setScore(calculatePauseScore(pauseAnalysis));
//        pauseAnalysis.setFeedback(generatePauseFeedback(pauseAnalysis));
//
//        result.setIntonation(intonation);
//        result.setEnergy(energy);
//        result.setPauses(pauseAnalysis);
//        return result;
//    }
//
//    // Helper: RMS calculation
//    private double calculateRMS(float[] buffer) {
//        double sum = 0;
//        for (float sample : buffer) sum += sample * sample;
//        return Math.sqrt(sum / buffer.length);
//    }
//
//    class PauseState {
//        boolean inPause = false;
//        double pauseStart = 0;
//
//        private void handlePauseDetection(double rms, double timeStamp, List<AnalysisResult.PauseSegment> pauses) {
//            final double ENERGY_PAUSE_THRESHOLD = 0.01, MIN_PAUSE_DURATION = 0.3;      // seconds
//
//            if (rms < ENERGY_PAUSE_THRESHOLD) {
//                if (!inPause) {
//                    inPause = true;
//                    pauseStart = timeStamp;
//                }
//            } else {
//                if (inPause) {
//                    inPause = false;
//                    double duration = timeStamp - pauseStart;
//                    if (duration >= MIN_PAUSE_DURATION) {
//                        pauses.add(new AnalysisResult.PauseSegment(pauseStart, timeStamp, duration));
//                    }
//                }
//            }
//        }
//    }
//
//    // Helper: Standard deviation for energy
//    private static double calculateEnergyStdDev(List<OptimizedAudioAnalysisService.TimeValuePair> energySeries, double avgEnergy) {
//        double sumSq = 0;
//        for (OptimizedAudioAnalysisService.TimeValuePair pair : energySeries) {
//            double diff = pair.getValue() - avgEnergy;
//            sumSq += diff * diff;
//        }
//        return energySeries.size() > 1 ? Math.sqrt(sumSq / (energySeries.size() - 1)) : 0;
//    }
//}
