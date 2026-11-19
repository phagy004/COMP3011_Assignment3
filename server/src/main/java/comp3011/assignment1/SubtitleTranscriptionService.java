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

//Responsible for sending media audio to OpenAI Whisper and converting
//the returned transcription timing information into subtitle items.
@Service
public class SubtitleTranscriptionService {

    private static final String OPENAI_URL =
            "https://api.openai.com/v1/audio/transcriptions";

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    //Tracks the total amount of audio transcribed by this server.
    private final SubtitleUsageService usageService;

    public SubtitleTranscriptionService(
            SubtitleUsageService usageService) {

        this.usageService = usageService;
    }

    public List<SubtitleItem> transcribe(
            MultipartFile file) throws IOException {

        String apiKey = getApiKey();

        //Spring multipart files cannot be inserted directly into every
        //HTTP request, so the uploaded bytes are wrapped as a resource.
        ByteArrayResource audioResource =
                new ByteArrayResource(file.getBytes()) {

            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        //Preserve the uploaded audio content type when forwarding it.
        HttpHeaders fileHeaders =
                new HttpHeaders();

        if (file.getContentType() != null) {
            fileHeaders.setContentType(
                    MediaType.parseMediaType(
                            file.getContentType()));
        } else {
            fileHeaders.setContentType(
                    MediaType.parseMediaType(
                            "audio/mpeg"));
        }

        HttpEntity<ByteArrayResource> filePart =
                new HttpEntity<>(
                        audioResource,
                        fileHeaders);

        //Build the multipart form required by the OpenAI audio endpoint.
        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        body.add("file", filePart);

        //These values are required by the Assignment 3 specification.
        //Verbose JSON is required because normal JSON does not contain
        //the word and segment timing data needed by the video player.
        body.add("model", "whisper-1");
        body.add("response_format", "verbose_json");
        body.add("timestamp_granularities[]", "word");
        body.add("timestamp_granularities[]", "segment");

        HttpHeaders headers =
                new HttpHeaders();

        //OpenAI authenticates requests using a bearer token.
        headers.setBearerAuth(apiKey);
        headers.setContentType(
                MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> request =
                new HttpEntity<>(
                        body,
                        headers);

        RestTemplate restTemplate =
                new RestTemplate();

        try {
            //Send the extracted media audio to Whisper.
            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            OPENAI_URL,
                            request,
                            String.class);

            //Convert the verbose JSON response into subtitle objects.
            return readSubtitles(
                    response.getBody());

        } catch (HttpStatusCodeException error) {
            //Printing the OpenAI response makes API problems easier
            //to diagnose without crashing the desktop video player.
            System.out.println(
                    "OpenAI error "
                    + error.getStatusCode()
                    + ": "
                    + error.getResponseBodyAsString());

            throw new IllegalStateException(
                    "OpenAI transcription failed");
        }
    }

    //Creates subtitle phrases from Whisper's word-level timing data.
    private List<SubtitleItem> readSubtitles(
            String response) throws IOException {

        if (response == null || response.isBlank()) {
            return List.of();
        }

        JsonNode json =
                objectMapper.readTree(response);

        //The global usage endpoint reports transcription time in seconds.
        JsonNode usage =
                json.path("usage");

        if (usage.has("seconds")) {
            usageService.addUsage(
                    usage.path("seconds")
                            .asDouble());
        } else {
            //Verbose Whisper responses also contain the media duration,
            //which can be used if the usage object does not provide seconds.
            usageService.addUsage(
                    json.path("duration")
                            .asDouble());
        }

        //Word timestamps are used to build shorter subtitle phrases.
        JsonNode words =
                json.path("words");

        //Segments are still required because they contain the confidence
        //values used by the assignment's no-speech filtering rule.
        JsonNode segments =
                json.path("segments");

        List<SubtitleItem> subtitles =
                new ArrayList<>();

        StringBuilder text =
                new StringBuilder();

        double start = -1;
        double end = 0;
        double previousEnd = -1;

        for (JsonNode word : words) {
            String value =
                    word.path("word")
                            .asText()
                            .trim();

            double wordStart =
                    word.path("start")
                            .asDouble();

            double wordEnd =
                    word.path("end")
                            .asDouble();

            if (value.isEmpty()) {
                continue;
            }

            //The word is only used if its parent segment passes the
            //required no-speech confidence check.
            if (!isAllowedWord(
                    wordStart,
                    wordEnd,
                    segments)) {

                continue;
            }

            //The first accepted word defines the start of a new subtitle.
            if (start < 0) {
                start = wordStart;
            }

            //A noticeable pause is treated as a natural subtitle boundary.
            boolean longPause =
                    previousEnd >= 0
                    && wordStart - previousEnd > 0.6;

            //42 characters are allowed per line and at most two lines
            //are displayed, giving a practical maximum of 84 characters.
            boolean tooLong =
                    text.length()
                    + value.length()
                    + 1 > 84;

            //No subtitle should remain active for more than seven seconds.
            boolean tooLongOnScreen =
                    wordEnd - start > 7.0;

            if (text.length() > 0
                    && (longPause
                    || tooLong
                    || tooLongOnScreen)) {

                addSubtitle(
                        subtitles,
                        text,
                        start,
                        end);

                text.setLength(0);
                start = wordStart;
            }

            if (text.length() > 0) {
                text.append(" ");
            }

            text.append(value);

            end = wordEnd;
            previousEnd = wordEnd;

            //Sentence punctuation provides another natural place
            //to finish the current subtitle phrase.
            if (endsSentence(value)) {
                addSubtitle(
                        subtitles,
                        text,
                        start,
                        end);

                text.setLength(0);
                start = -1;
                previousEnd = -1;
            }
        }

        //Add any words remaining after the final loop iteration.
        if (text.length() > 0 && start >= 0) {
            addSubtitle(
                    subtitles,
                    text,
                    start,
                    end);
        }

        System.out.println(
                "Whisper returned "
                + subtitles.size()
                + " subtitles");

        return subtitles;
    }

    //Checks which Whisper segment contains the current word.
    //A segment is ignored only when BOTH confidence conditions
    //specified in the assignment are true.
    private boolean isAllowedWord(
            double wordStart,
            double wordEnd,
            JsonNode segments) {

        if (!segments.isArray()
                || segments.isEmpty()) {

            return true;
        }

        //Using the middle of the word makes matching it to a segment simple.
        double middle =
                (wordStart + wordEnd) / 2.0;

        for (JsonNode segment : segments) {
            double segmentStart =
                    segment.path("start")
                            .asDouble();

            double segmentEnd =
                    segment.path("end")
                            .asDouble();

            if (middle < segmentStart
                    || middle > segmentEnd) {

                continue;
            }

            double noSpeechProb =
                    segment.path("no_speech_prob")
                            .asDouble();

            double aveLogProb =
                    segment.path("avg_logprob")
                            .asDouble();

            //Do not reject the segment if only one condition is true.
            return !(noSpeechProb > 0.6
                    && aveLogProb < -1.0);
        }

        return true;
    }

    //Adds one completed subtitle phrase while enforcing the
    //Assignment 3 display duration rules.
    private void addSubtitle(
            List<SubtitleItem> subtitles,
            StringBuilder text,
            double start,
            double end) {

        if (text.length() == 0 || start < 0) {
            return;
        }

        //Every subtitle must remain visible for at least one second.
        double displayEnd =
                Math.max(
                        end,
                        start + 1.0);

        //No subtitle should remain visible for more than seven seconds.
        displayEnd =
                Math.min(
                        displayEnd,
                        start + 7.0);

        subtitles.add(
                new SubtitleItem(
                        text.toString().trim(),
                        start,
                        displayEnd));
    }

    //Sentence-ending punctuation is preferred when grouping words.
    private boolean endsSentence(String word) {
        return word.endsWith(".")
                || word.endsWith("?")
                || word.endsWith("!");
    }

    //The API key can be supplied either through the environment or
    //through key.txt during local testing and the viva.
    private String getApiKey() throws IOException {
        String key =
                System.getenv(
                        "OPENAI_API_KEY");

        //Environment variables are checked first so the key does not
        //need to exist as a project file.
        if (key != null && !key.isBlank()) {
            return key.trim();
        }

        Path keyFile =
                Path.of("key.txt");

        //When the application is started from the Assignment3 root,
        //the server key may instead be inside the server directory.
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