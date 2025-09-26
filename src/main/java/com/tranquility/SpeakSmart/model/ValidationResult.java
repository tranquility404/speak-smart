package com.tranquility.SpeakSmart.model;

import lombok.Data;

@Data
public class ValidationResult {
    private boolean valid;
    private String errorMessage;
    private double durationSeconds;
    private byte[] audioFile;
}
