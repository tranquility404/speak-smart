package com.tranquility.SpeakSmart.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LlmUtils {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static JsonNode extractJsonFromLlm(String data) {
        // Regex to find JSON starting with '{' until the last '}'
        Pattern pattern = Pattern.compile("\\{.*}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(data);

        if (!matcher.find()) {
            System.out.println("Data not found in LLM output");
            return mapper.createObjectNode(); // return empty JSON {}
        }

        try {
            String jsonStr = matcher.group();
            return mapper.readTree(jsonStr);
        } catch (Exception e) {
            e.printStackTrace();
            return mapper.createObjectNode(); // return empty JSON {} on error
        }
    }
}
