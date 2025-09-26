package com.tranquility.SpeakSmart.service;

import com.tranquility.SpeakSmart.model.AnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class ChartGenerationService {

    @Autowired
    private CloudinaryService cloudinaryService;

    /**
     * Generate and upload speech rate chart
     */
    public String generateSpeechRateChart(AnalysisResult.SpeechRateAnalysis speechRate) {
        try {
            if (speechRate.getSegments() == null || speechRate.getSegments().isEmpty()) {
                log.warn("No segments available for speech rate chart generation");
                return null;
            }

            List<AnalysisResult.SpeechSegment> segments = speechRate.getSegments();

            // Create time series data
            double[] timeData = segments.stream().mapToDouble(AnalysisResult.SpeechSegment::getStart).toArray();
            double[] wpmData = segments.stream().mapToDouble(AnalysisResult.SpeechSegment::getSpeechRate).toArray();

            // Create chart
            XYChart chart = new XYChartBuilder()
                    .width(800)
                    .height(400)
                    .title("Speech Rate Over Time")
                    .xAxisTitle("Time (seconds)")
                    .yAxisTitle("Words Per Minute (WPM)")
                    .build();

            // Customize chart style
            chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);

            // Add data series
            XYSeries speechSeries = chart.addSeries("Speech Rate", timeData, wpmData);
            speechSeries.setMarkerColor(Color.BLUE);
            speechSeries.setLineColor(Color.BLUE);

            // Add average line
            double avgWpm = speechRate.getAvgSpeechRate();
            double[] avgLine = new double[timeData.length];
            Arrays.fill(avgLine, avgWpm);
            XYSeries avgSeries = chart.addSeries("Average (" + String.format("%.1f", avgWpm) + " WPM)", timeData, avgLine);
            avgSeries.setMarkerColor(Color.RED);
            avgSeries.setLineColor(Color.RED);

            return uploadChartToCloudinary(chart, "speech_rate_chart_" + System.currentTimeMillis());

        } catch (Exception e) {
            log.error("Error generating speech rate chart", e);
            return null;
        }
    }

    /**
     * Generate and upload intonation chart
     */
    public XYChart generateIntonationChart(AnalysisResult.IntonationAnalysis intonation, List<SpeechAnalysisService.TimeValuePair> pitchTimeSeries) {
        try {
            if (pitchTimeSeries == null || pitchTimeSeries.isEmpty()) {
                log.warn("No data available for intonation chart generation");
                return null;
            }

            // Create time series data
            double[] timeData = pitchTimeSeries.stream().mapToDouble(SpeechAnalysisService.TimeValuePair::getTime).toArray();
            double[] pitchData = pitchTimeSeries.stream().mapToDouble(SpeechAnalysisService.TimeValuePair::getValue).toArray();

            // Create chart
            XYChart chart = new XYChartBuilder()
                    .width(800)
                    .height(400)
                    .title("Pitch Variation Over Time")
                    .xAxisTitle("Time (seconds)")
                    .yAxisTitle("Pitch (Hz)")
                    .build();

            // Customize chart style
            chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);

            // Add data series
            XYSeries pitchSeries = chart.addSeries("Pitch", timeData, pitchData);
            pitchSeries.setMarkerColor(Color.GREEN);
            pitchSeries.setLineColor(Color.GREEN);

            // Add average line
            double avgPitch = intonation.getAveragePitch();
            double[] avgLine = new double[timeData.length];
            for (int i = 0; i < avgLine.length; i++) {
                avgLine[i] = avgPitch;
            }
            XYSeries avgSeries = chart.addSeries("Average (" + String.format("%.1f", avgPitch) + " Hz)", timeData, avgLine);
            avgSeries.setMarkerColor(Color.ORANGE);
            avgSeries.setLineColor(Color.ORANGE);

            return chart;

        } catch (Exception e) {
            log.error("Error generating intonation chart", e);
            return null;
        }
    }

    /**
     * Generate and upload energy chart
     */
//    public String generateEnergyChart(AnalysisResult.EnergyAnalysis energy) {
//        try {
//            if (energy.getSegments() == null || energy.getSegments().isEmpty()) {
//                log.warn("No segments available for energy chart generation");
//                return null;
//            }
//
//            List<AnalysisResult.EnergySegment> segments = energy.getSegments();
//
//            // Create time series data
//            double[] timeData = segments.stream().mapToDouble(AnalysisResult.EnergySegment::getStartTime).toArray();
//            double[] energyData = segments.stream().mapToDouble(AnalysisResult.EnergySegment::getEnergy).toArray();
//
//            // Create chart
//            XYChart chart = new XYChartBuilder()
//                    .width(800)
//                    .height(400)
//                    .title("Energy Level Over Time")
//                    .xAxisTitle("Time (seconds)")
//                    .yAxisTitle("Energy Level")
//                    .build();
//
//            // Customize chart style
//            chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
//
//            // Add data series
//            XYSeries energySeries = chart.addSeries("Energy", timeData, energyData);
//            energySeries.setFillColor(new Color(255, 165, 0, 100));
//            energySeries.setLineColor(Color.ORANGE);
//
//            return uploadChartToCloudinary(chart, "energy_chart_" + System.currentTimeMillis());
//
//        } catch (Exception e) {
//            log.error("Error generating energy chart", e);
//            return null;
//        }
//    }

    /**
     * Upload chart to Cloudinary and return URL
     */
    public String uploadChartToCloudinary(XYChart chart, String filename) {
        try {
            // Convert chart to bytes
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BitmapEncoder.saveBitmap(chart, outputStream, BitmapEncoder.BitmapFormat.PNG);
            byte[] chartBytes = outputStream.toByteArray();

            // Upload to Cloudinary
            var uploadResult = cloudinaryService.uploadFile(chartBytes, filename + ".png", "image/png", "charts");
            return (String) uploadResult.get("secure_url");

        } catch (Exception e) {
            log.error("Error uploading chart to Cloudinary", e);
            return null;
        }
    }

    /**
     * Create a MultipartFile from byte array
     */
    private MultipartFile createMultipartFile(byte[] content, String filename, String contentType) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return filename;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public boolean isEmpty() {
                return content == null || content.length == 0;
            }

            @Override
            public long getSize() {
                return content.length;
            }

            @Override
            public byte[] getBytes() throws IOException {
                return content;
            }

            @Override
            public java.io.InputStream getInputStream() throws IOException {
                return new java.io.ByteArrayInputStream(content);
            }

            @Override
            public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                // Not implemented for this use case
                throw new UnsupportedOperationException("Transfer to file not supported");
            }
        };
    }
}
