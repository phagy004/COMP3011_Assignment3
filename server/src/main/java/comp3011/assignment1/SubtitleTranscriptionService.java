package comp3011.assignment1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SubtitleTranscriptionService {

    private static final String OPENAI_URL =
            "https://api.openai.com/v1/audio/transcriptions";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SubtitleUsageService usageService;

    public SubtitleTranscriptionService(
            SubtitleUsageService usageService) {

        this.usageService = usageService;
    }

    public List<SubtitleItem> transcribe(
            MultipartFile file) throws IOException {

        String apiKey = getApiKey();

        ByteArrayResource audioResource =
                new ByteArrayResource(file.getBytes()) {

            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();

        if (file.getContentType() != null) {
            fileHeaders.setContentType(
                    MediaType.parseMediaType(
                            file.getContentType()));
        } else {
            fileHeaders.setContentType(
                    MediaType.parseMediaType("audio/mpeg"));
        }

        HttpEntity<ByteArrayResource> filePart =
                new HttpEntity<>(
                        audioResource,
                        fileHeaders);

        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        body.add("file", filePart);
        body.add("model", "whisper-1");
        body.add("response_format", "verbose_json");

        body.add(
                "timestamp_granularities[]",
                "word");

        body.add(
                "timestamp_granularities[]",
                "segment");

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(apiKey);
        headers.setContentType(
                MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> request =
                new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            OPENAI_URL,
                            request,
                            String.class);

            return readSubtitles(
                    response.getBody());

        } catch (HttpStatusCodeException error) {

            System.out.println(
                    "OpenAI error "
                    + error.getStatusCode()
                    + ": "
                    + error.getResponseBodyAsString());

            throw new IllegalStateException(
                    "OpenAI transcription failed");
        }
    }

    private List<SubtitleItem> readSubtitles(
            String response) throws IOException {

        if (response == null || response.isBlank()) {
            return List.of();
        }

        JsonNode json =
                objectMapper.readTree(response);

        JsonNode usage =
                json.path("usage");

        if (usage.has("seconds")) {
            usageService.addUsage(
                    usage.path("seconds").asDouble());
        } else {
            usageService.addUsage(
                    json.path("duration").asDouble());
        }

        JsonNode segments =
                json.path("segments");

        List<SubtitleItem> subtitles =
                new ArrayList<>();

        for (JsonNode segment : segments) {

            String text =
                    segment.path("text")
                            .asText()
                            .trim();

            double start =
                    segment.path("start")
                            .asDouble();

            double end =
                    segment.path("end")
                            .asDouble();

            double noSpeechProb =
                    segment.path("no_speech_prob")
                            .asDouble();

            double aveLogProb =
                    segment.path("avg_logprob")
                            .asDouble();

            if (noSpeechProb > 0.6
                    && aveLogProb < -1.0) {

                continue;
            }

            if (text.isEmpty()) {
                continue;
            }

            double displayEnd =
                    Math.max(
                            end,
                            start + 1.0);

            displayEnd =
                    Math.min(
                            displayEnd,
                            start + 7.0);

            subtitles.add(
                    new SubtitleItem(
                            text,
                            start,
                            displayEnd));
        }

        System.out.println(
                "Whisper returned "
                + subtitles.size()
                + " subtitles");

        return subtitles;
    }

    private String getApiKey()
            throws IOException {

        String key =
                System.getenv(
                        "OPENAI_API_KEY");

        if (key != null
                && !key.isBlank()) {

            return key.trim();
        }

        Path keyFile =
                Path.of("key.txt");

        if (!Files.exists(keyFile)) {
            keyFile =
                    Path.of(
                            "server",
                            "key.txt");
        }

        if (Files.exists(keyFile)) {
            return Files
                    .readString(keyFile)
                    .trim();
        }

        throw new IllegalStateException(
                "OPENAI_API_KEY or key.txt is required");
    }
}