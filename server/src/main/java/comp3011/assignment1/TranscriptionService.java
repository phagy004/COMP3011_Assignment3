package comp3011.assignment1;

import java.util.Map;

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
import org.springframework.web.multipart.MultipartFile;

@Service
public class TranscriptionService {

    //API key used to connect to OpenAI
    //The key is loaded from the environment so it is not stored in the code
    private final String openAiApiKey;

    //Used to store token usage from each transcription request
    private final StatisticsService statisticsService;

    public TranscriptionService(
            @Value("#{systemEnvironment['OPENAI_API_KEY']}") String openAiApiKey,
            StatisticsService statisticsService) {

        this.openAiApiKey = openAiApiKey;
        this.statisticsService = statisticsService;
    }

    public String processAudio(MultipartFile audioFile) {

        //Check that an audio file was received
        if (audioFile == null || audioFile.isEmpty()) {
            throw new IllegalArgumentException("Audio file is empty");
        }

        //The OpenAI request cannot be sent if the API key is missing
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY is not configured");
        }

        try {
            //Convert the uploaded audio into a resource for the HTTP request
            ByteArrayResource audioResource =
                    new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    //Give the audio a file name so OpenAI can recognise the format
                    return "recording.webm";
                }
            };

            //Set the content type for the audio file
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(
                    MediaType.parseMediaType("audio/webm"));

            //Put the audio data and its headers together
            HttpEntity<ByteArrayResource> filePart =
                    new HttpEntity<>(audioResource, fileHeaders);

            //Create the form data that will be sent to OpenAI
            MultiValueMap<String, Object> body =
                    new LinkedMultiValueMap<>();

            //Add the recorded audio file
            body.add("file", filePart);

            //Choose the transcription model required by Assignment 1
            body.add("model", "gpt-4o-mini-transcribe");

            //Ask OpenAI to return the result as JSON
            body.add("response_format", "json");

            //Create headers for the complete OpenAI request
            HttpHeaders headers = new HttpHeaders();

            //Add the API key as a bearer token
            headers.setBearerAuth(openAiApiKey);

            //The request contains both text fields and an audio file
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            //Combine the request body and headers
            HttpEntity<MultiValueMap<String, Object>> request =
                    new HttpEntity<>(body, headers);

            //Used to send the HTTP request to OpenAI
            RestTemplate restTemplate = new RestTemplate();

            //Send the audio to the OpenAI transcription API
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(
                            "https://api.openai.com/v1/audio/transcriptions",
                            request,
                            Map.class);

            //Get the JSON response body returned by OpenAI
            Map<?, ?> apiResponse = response.getBody();

            //Make sure OpenAI returned a response
            if (apiResponse == null) {
                throw new IllegalStateException(
                        "Empty response from OpenAI");
            }

            //Get the transcription text from the response
            Object text = apiResponse.get("text");

            if (text == null) {
                throw new IllegalStateException(
                        "No transcription text returned");
            }

            //Get token usage so it can be added to the global statistics
            Object usageObject = apiResponse.get("usage");

            if (usageObject instanceof Map<?, ?> usage) {
                Object input = usage.get("input_tokens");
                Object output = usage.get("output_tokens");

                long inputTokens = 0;
                long outputTokens = 0;

                if (input instanceof Number) {
                    inputTokens = ((Number) input).longValue();
                }

                if (output instanceof Number) {
                    outputTokens = ((Number) output).longValue();
                }

                statisticsService.addTokens(
                        inputTokens,
                        outputTokens);
            }

            return text.toString();

        } catch (Exception error) {
            throw new IllegalStateException(
                    "Transcription failed: " + error.getMessage());
        }
    }
}