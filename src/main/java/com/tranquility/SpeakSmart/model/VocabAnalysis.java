package com.tranquility.SpeakSmart.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@NoArgsConstructor
public class VocabAnalysis {
    private String repeatedWords;    // List<Map<String, Object>>
    private String grammaticalErrors; // List<Map<String, Object>>
    private String longSentences;  // List<Map<String, Object>>
    private String modifiedText;
    private String fancyText;
    private String meanings;

    public VocabAnalysis(JsonNode data) {
        try {
            this.repeatedWords = data.get("repeated_words").toString();
            this.grammaticalErrors = data.get("grammatical_errors").toString();
            this.longSentences = data.get("long_sentences").toString();
            this.modifiedText = data.get("modified_text").toString();
            this.fancyText = data.get("fancy_text").toString();
            this.meanings = data.get("meanings").toString();
        } catch (Exception e) {
            log.error("There was some error is trying to parse llm vocab response: %s %s".formatted(e.getMessage(), data.toString()));
        }
    }
}
