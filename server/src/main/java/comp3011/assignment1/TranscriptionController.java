package comp3011.assignment1;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class TranscriptionController {

    //Service used to process the uploaded audio
    private final TranscriptionService transcriptionService;

    public TranscriptionController(
            TranscriptionService transcriptionService) {

        //Save the service so it can be used by the controller methods
        this.transcriptionService =
                transcriptionService;
    }

    //Simple endpoint used to check that the controller is working
    @GetMapping("/api/test")
    public String test() {

        return "Controller is working";
    }

    //Receive the recorded audio sent from the web page
    @PostMapping("/api/transcribe")
    public ResponseEntity<Map<String, Object>> transcribe(
            @RequestParam("audio") MultipartFile audioFile) {

        //Store the data that will be returned to the browser
        Map<String, Object> response =
                new HashMap<>();

        try {

            //Send the audio file to the transcription service
            String text =
                    transcriptionService
                            .processAudio(audioFile);

            //Add the transcription text to the response
            response.put(
                    "text",
                    text
            );

            //Return HTTP 200 when the transcription is successful
            return ResponseEntity
                    .ok(response);

        } catch (IllegalArgumentException error) {

            //This happens when the audio sent by the user is not valid
            response.put(
                    "message",
                    error.getMessage()
            );

            //Return HTTP 400 because the request has a problem
            return ResponseEntity
                    .badRequest()
                    .body(response);

        } catch (IllegalStateException error) {

            //This happens when the transcription process cannot finish
            response.put(
                    "message",
                    error.getMessage()
            );

            //Return HTTP 500 when the server cannot complete the request
            return ResponseEntity
                    .internalServerError()
                    .body(response);
        }
    }
}