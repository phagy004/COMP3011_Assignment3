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

    public SubtitleTranscriptionService(SubtitleUsageService usageService) {
        this.usageService = usageService;
    }

    public List<SubtitleItem> transcribe(MultipartFile file) throws IOException {
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
                    MediaType.parseMediaType(file.getContentType()));
        } else {
            fileHeaders.setContentType(
                    MediaType.parseMediaType("audio/mpeg"));
        }

        HttpEntity<ByteArrayResource> filePart =
                new HttpEntity<>(audioResource, fileHeaders);

        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        body.add("file", filePart);
        body.add("model", "whisper-1");
        body.add("response_format", "verbose_json");
        body.add("timestamp_granularities[]", "word");
        body.add("timestamp_granularities[]", "segment");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> request =
                new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            OPENAI_URL,
                            request,
                            String.class);

            return readSubtitles(response.getBody());

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

    private List<SubtitleItem> readSubtitles(String response) throws IOException {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        JsonNode json = objectMapper.readTree(response);

        JsonNode usage = json.path("usage");

        if (usage.has("seconds")) {
            usageService.addUsage(usage.path("seconds").asDouble());
        } else {
            usageService.addUsage(json.path("duration").asDouble());
        }

        JsonNode words = json.path("words");
        JsonNode segments = json.path("segments");

        List<SubtitleItem> subtitles = new ArrayList<>();

        StringBuilder text = new StringBuilder();
        double start = -1;
        double end = 0;
        double previousEnd = -1;

        for (JsonNode word : words) {
            String value = word.path("word").asText().trim();
            double wordStart = word.path("start").asDouble();
            double wordEnd = word.path("end").asDouble();

            if (value.isEmpty()) {
                continue;
            }

            if (!isAllowedWord(wordStart, wordEnd, segments)) {
                continue;
            }

            if (start < 0) {
                start = wordStart;
            }

            boolean longPause =
                    previousEnd >= 0 && wordStart - previousEnd > 0.6;

            boolean tooLong =
                    text.length() + value.length() + 1 > 84;

            boolean tooLongOnScreen =
                    wordEnd - start > 7.0;

            if (text.length() > 0
                    && (longPause || tooLong || tooLongOnScreen)) {

                addSubtitle(subtitles, text, start, end);

                text.setLength(0);
                start = wordStart;
            }

            if (text.length() > 0) {
                text.append(" ");
            }

            text.append(value);
            end = wordEnd;
            previousEnd = wordEnd;

            if (endsSentence(value)) {
                addSubtitle(subtitles, text, start, end);

                text.setLength(0);
                start = -1;
                previousEnd = -1;
            }
        }

        if (text.length() > 0 && start >= 0) {
            addSubtitle(subtitles, text, start, end);
        }

        System.out.println(
                "Whisper returned "
                + subtitles.size()
                + " subtitles");

        return subtitles;
    }

    private boolean isAllowedWord(
            double wordStart,
            double wordEnd,
            JsonNode segments) {

        if (!segments.isArray() || segments.isEmpty()) {
            return true;
        }

        double middle = (wordStart + wordEnd) / 2.0;

        for (JsonNode segment : segments) {
            double segmentStart =
                    segment.path("start").asDouble();

            double segmentEnd =
                    segment.path("end").asDouble();

            if (middle < segmentStart || middle > segmentEnd) {
                continue;
            }

            double noSpeechProb =
                    segment.path("no_speech_prob").asDouble();

            double aveLogProb =
                    segment.path("avg_logprob").asDouble();

            return !(noSpeechProb > 0.6
                    && aveLogProb < -1.0);
        }

        return true;
    }

    private void addSubtitle(
            List<SubtitleItem> subtitles,
            StringBuilder text,
            double start,
            double end) {

        if (text.length() == 0 || start < 0) {
            return;
        }

        double displayEnd = Math.max(end, start + 1.0);
        displayEnd = Math.min(displayEnd, start + 7.0);

        subtitles.add(
                new SubtitleItem(
                        text.toString().trim(),
                        start,
                        displayEnd));
    }

    private boolean endsSentence(String word) {
        return word.endsWith(".")
                || word.endsWith("?")
                || word.endsWith("!");
    }

    private String getApiKey() throws IOException {
        String key = System.getenv("OPENAI_API_KEY");

        if (key != null && !key.isBlank()) {
            return key.trim();
        }

        Path keyFile = Path.of("key.txt");

        if (!Files.exists(keyFile)) {
            keyFile = Path.of("server", "key.txt");
        }

        if (Files.exists(keyFile)) {
            return Files.readString(keyFile).trim();
        }

        throw new IllegalStateException(
                "OPENAI_API_KEY or key.txt is required");
    }
}