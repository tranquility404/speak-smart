package com.tranquility.SpeakSmart.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

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
        this.repeatedWords = data.get("repeated_words").toString();
        this.grammaticalErrors = data.get("grammatical_errors").toString();
        this.longSentences = data.get("long_sentences").toString();
        this.modifiedText = data.get("modified_text").toString();
        this.fancyText = data.get("fancy_text").toString();
        this.meanings = data.get("meanings").toString();
    }
}
