package comp3011;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

//Handles communication between the desktop video player and the subtitle server.
public class SubtitleService {

    //The Assignment 3 server runs locally using Spring Boot.
    private static final String SUBTITLE_URL =
            "http://localhost:8080/api/v1/subtitles";

    //A few retries allow subtitles to recover from a temporary
    //server or network problem without stopping the video.
    private static final int MAX_RETRIES = 3;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AudioExtractor audioExtractor = new AudioExtractor();

    public CompletableFuture<List<SubtitleSegment>> getSubtitles(File videoFile) {
        //The media may have been removed, for example if it was opened
        //from a USB drive which was disconnected.
        if (videoFile == null || !videoFile.exists()) {
            return CompletableFuture.completedFuture(List.of());
        }

        //Audio extraction can take some time, so it is performed on a
        //background thread instead of blocking the JavaFX UI thread.
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return audioExtractor.extract(videoFile);
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                })

                //Once the audio file is available, send it to the server.
                .thenCompose(audioFile ->
                        sendWithRetry(audioFile, 0))

                //Subtitle failure should not cause video playback to fail.
                .exceptionally(error -> {
                    System.out.println(
                            "Could not load subtitles: "
                            + error.getMessage());

                    return List.of();
                });
    }

    //Attempts the request again if the server cannot currently be reached.
    private CompletableFuture<List<SubtitleSegment>> sendWithRetry(
            File audioFile,
            int attempt) {

        return sendAudio(audioFile)
                .exceptionallyCompose(error -> {
                    //Stop retrying after the maximum number of attempts.
                    if (attempt >= MAX_RETRIES) {
                        System.out.println("Subtitle server unavailable.");
                        return CompletableFuture.completedFuture(List.of());
                    }

                    System.out.println("Subtitle request failed. Retrying...");

                    //Wait briefly before another attempt.
                    return CompletableFuture
                            .runAsync(
                                    () -> {},
                                    CompletableFuture.delayedExecutor(
                                            2,
                                            TimeUnit.SECONDS))
                            .thenCompose(ignore ->
                                    sendWithRetry(
                                            audioFile,
                                            attempt + 1));
                });
    }

    //Creates a multipart HTTP request containing the extracted MP3 file.
    private CompletableFuture<List<SubtitleSegment>> sendAudio(File audioFile) {
        try {
            String boundary = "SubtitleBoundary";

            //Multipart requests require a header before the binary file data.
            String header =
                    "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\""
                    + audioFile.getName() + "\"\r\n"
                    + "Content-Type: audio/mpeg\r\n\r\n";

            String footer =
                    "\r\n--" + boundary + "--\r\n";

            HttpRequest.BodyPublisher body =
                    HttpRequest.BodyPublishers.concat(
                            HttpRequest.BodyPublishers.ofByteArray(
                                    header.getBytes(StandardCharsets.UTF_8)),
                            HttpRequest.BodyPublishers.ofFile(
                                    audioFile.toPath()),
                            HttpRequest.BodyPublishers.ofByteArray(
                                    footer.getBytes(StandardCharsets.UTF_8)));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUBTITLE_URL))
                    .header(
                            "Content-Type",
                            "multipart/form-data; boundary=" + boundary)
                    .POST(body)
                    .build();

            //The async HTTP client allows playback to continue while
            //the server performs the Whisper transcription.
            return client.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.ofString())
                    .thenCompose(response -> {
                        if (response.statusCode() != 200) {
                            System.out.println(
                                    "Subtitle server error "
                                    + response.statusCode()
                                    + ": "
                                    + response.body());

                            return CompletableFuture.failedFuture(
                                    new RuntimeException(
                                            "Server returned "
                                            + response.statusCode()));
                        }

                        return CompletableFuture.completedFuture(
                                readResponse(response.body()));
                    });

        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    //Converts the JSON returned by Spring Boot into objects understood
    //by the video frame processor.
    private List<SubtitleSegment> readResponse(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            List<SubtitleSegment> subtitles = new ArrayList<>();

            for (JsonNode item : json) {
                String text = item.path("text").asText();
                double start = item.path("start").asDouble();
                double end = item.path("end").asDouble();

                subtitles.add(
                        new SubtitleSegment(
                                text,
                                start,
                                end));
            }

            return subtitles;

        } catch (Exception error) {
            throw new RuntimeException(
                    "Could not read subtitle response",
                    error);
        }
    }
}