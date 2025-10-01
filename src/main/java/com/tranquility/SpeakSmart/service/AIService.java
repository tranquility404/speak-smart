package com.tranquility.SpeakSmart.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AIService {

    @Value("${groq.api.key}")
    public String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    // ------------------- Transcription -------------------
    public Map<String, Object> transcribe(byte[] fileBytes, String fileName) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(apiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        body.add("model", "whisper-large-v3");
        body.add("response_format", "verbose_json");

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity("https://api.groq.com/openai/v1/audio/transcriptions", request, Map.class);
        System.out.println("Transcription: " + response.getBody());
        return response.getBody();
    }

    // ------------------- Public Methods -------------------
    public String getLlmAnalysis(String transcription) throws Exception {
        String systemMessage = """
                Generate No Preamble. Assume you are an excellent public speaker coach. 
                Note:
                - don't change language of the sentence.
                - don't change the perspective or meaning of the sentence.
                - each of the list will be a set (no duplicates).
                Create a list of:
                - "repeated_words": list of top 10 repeated words with repetition count (only include meaningful words, no helping or stop words).
                - "grammatical_errors": list of top 5 grammatical errors with correct sentence & very short explanation (only include grammatical errors) (keep explanation very short and easy to understand).
                - "long_sentences": list of top 5 long sentences with suggestion (suggestion will be moderately optimized length sentence with concise & appropriate words).
                Generate text:
                - "modified_text": by optimizing all long sentences, correct grammatical errors, remove repeated words.
                - "fancy_text": by using fancy & sophisticated words while maintaining conciseness.
                - "meanings": list of top 5 fancy words used in the "fancy_text" & their meanings in very short and easy to understand language.
                Output format:
                {
                    "repeated_words": [ { "word": "", "count": number } ],
                    "grammatical_errors": [ { "sentence": "", "correct": "",  "explanation": "" } ],
                    "long_sentences": [ { "sentence": "", "suggestion": "" } ],
                    "modified_text": "",
                    "fancy_text": "",
                    "meanings": [ { "word" : "", "meaning": "" } ]
                }
                For following text:
                """;
        return callGroqChatAPI(transcription, systemMessage, "qwen/qwen3-32b", 0.6, 4096);
    }

    public String generateRephrasals(String speechText) throws Exception {
        String systemMessage = """
                You are tasked with generating 5 different rephrasals of the provided speech text in the following styles:  
                1) Fancy and Sophisticated Words.  
                2) Technical Terms and Jargon.  
                3) Humorous with Funny Metaphors.  
                4) Concise and Appropriate Wording.  
                5) Philosophical and Deep Thinker.  
                No preamble, only return the JSON output.
                Output format:
                {
                    "fancy_and_sophisticated_words": "string",
                    "technical_terms_and_jargon": "string",
                    "humorous_with_funny_metaphors": "string",
                    "concise_and_appropriate_wording": "string",
                    "philosophical_and_deep_thinker": "string"
                }
                For following speech text:
                """;
        return callGroqChatAPI(speechText, systemMessage, "qwen/qwen3-32b", 0.6, 4096);
    }

    public String generateRandomTopics() throws Exception {
        String systemMessage = """
                Generate 5 random public speech topics that anyone can relate to.
                The topics should come from all kinds of categories, like everyday life, hobbies, school, history, or fun ideas.
                Each topic should be short and simple, 4-5 words long.
                Do not repeat similar topics.
                Return only the JSON output in this format:
               { "topics": ["Topic 1", "Topic 2", "Topic 3", "Topic 4", "Topic 5"] }
               """;
        return callGroqChatAPI(systemMessage, "", "llama-3.3-70b-versatile", 1.0, 1024);
    }

    public String generateSpeech(String topic) throws Exception {
        String systemPrompt = """
                Generate a speech of approximately 2 minutes in length on the topic: %s.
                Write it so that it **sounds like a person is speaking to an audience**—the speaker can go high and low in pitch, show excitement, pause for effect, and express feelings naturally.
                Keep the language simple and easy to read aloud, not like written content.
                Do not include any preamble, introductions, or conclusions.
                Respond strictly in this JSON format:
                { "speech": "Your generated speech here." }
            """.formatted(topic);
        return callGroqChatAPI(systemPrompt, "", "llama-3.3-70b-versatile", 1.0, 1024);
    }

    public String generateSlowFastDrill() throws Exception {
        String systemPrompt = """
            Generate a JSON-formatted list of 5 drill exercise texts. Each object should include:
            - "text": A clear and structured sentence with at least 200 and at max 300 characters for effective speech practice.
            - "type": "fast" or "slow", based on the expected speech rate.
            - "expected_speech_rate": A range in words per minute (WPM), categorized as follows:
             - "slow": 100-129 WPM
             - "fast": 176-220 WPM
            Ensure an even mix of all types and text associated with type should be of kind which is supposed to be spoken that way(slow or fast). No preamble, only return the JSON output.
            Output format:
            {
                "drill_exercises": [
                    {
                        "text": "",
                        "type": "",
                        "expected_speech_rate": [min, max]
                    },
                    ...
                ]
            }
            """;
        return callGroqChatAPI(systemPrompt, "", "llama-3.3-70b-versatile", 1.0, 1024);
    }

    public String generateMockInterview() throws Exception {
        String systemPrompt = """
            You are an AI assistant that generates mock interview questions in JSON format. The questions should be broad, open-ended, and suitable for general job interviews. Ensure the questions cover various categories such as Personal & Behavioral, Communication, Problem-Solving, Time Management, and Company-Specific. Avoid technical or job-specific questions. Keep the tone professional yet approachable. Your response must be a valid JSON object with no additional text, explanations, or preamble.
            """;
        String userContent = """
            Generate a JSON-formatted mock interview with 5 questions. Each question should include:
            - A "category" (e.g., Personal & Behavioral, Communication & Teamwork, etc.)
            - A "question" that is broad and open-ended.
            - "advice" that provides guidance on how to approach answering the question effectively.

            The JSON format should be:

            {
              "mock_interview": [
                {
                  "category": "Category Name",
                  "question": "Interview question text",
                  "advice": "Advice on how to answer the question effectively."
                },
                ...
              ]
            }

            Only return the JSON output. Do not include any preamble, explanations, or additional text.
            """;
        return callGroqChatAPI(userContent, systemPrompt, "llama-3.3-70b-versatile", 1.0, 1024);
    }

    // ------------------- Reusable Helper -------------------
    private String callGroqChatAPI(String userMessage, String systemMessage, String model,
                                   double temperature, int maxTokens) throws Exception {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemMessage),
                        Map.of("role", "user", "content", userMessage)
                ),
                "temperature", temperature,
                "max_completion_tokens", maxTokens,
                "top_p", 0.95
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity("https://api.groq.com/openai/v1/chat/completions", request, Map.class);

        Map<String, Object> choice = ((List<Map<String, Object>>) response.getBody().get("choices")).get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");

        return (String) message.get("content");
    }
}

