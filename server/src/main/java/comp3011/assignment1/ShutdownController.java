package comp3011.assignment1;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShutdownController {

    //Store the Spring application context so the server can be closed
    private final ConfigurableApplicationContext applicationContext;

    //Keep track of whether shutdown has already started
    private boolean shutdownStarted = false;

    public ShutdownController(
            ConfigurableApplicationContext applicationContext) {

        //Save the application context for later use
        this.applicationContext =
                applicationContext;
    }

    //Handle the graceful shutdown request
    @PostMapping("/api/v1/admin/shutdown")
    public synchronized ResponseEntity<Map<String, Object>> shutdownServer() {

        //Store the response that will be returned as JSON
        Map<String, Object> response =
                new HashMap<>();

        //Check if shutdown was already requested
        if (shutdownStarted) {

            //Return the current time for the error response
            response.put(
                    "timestamp",
                    Instant.now().toString()
            );

            //Return HTTP status 409 because shutdown is already running
            response.put(
                    "status",
                    409
            );

            //Return the error name required by the API
            response.put(
                    "error",
                    "Conflict"
            );

            //Explain why the request cannot be accepted
            response.put(
                    "message",
                    "Graceful shutdown is already in progress."
            );

            //Return the endpoint that caused the error
            response.put(
                    "path",
                    "/api/v1/admin/shutdown"
            );

            //Send HTTP 409 back to the client
            return ResponseEntity
                    .status(409)
                    .body(response);
        }

        //Mark shutdown as started so another request cannot start it again
        shutdownStarted = true;

        //Add the success message required by the API
        response.put(
                "message",
                "Graceful shutdown requested."
        );

        //Use another thread so the HTTP response can be returned first
        Thread shutdownThread =
                new Thread(() -> {

                    try {

                        //Wait a short time before closing the application
                        Thread.sleep(500);

                    } catch (InterruptedException error) {

                        //Restore the interrupted state if the thread is interrupted
                        Thread.currentThread()
                                .interrupt();
                    }

                    //Close the Spring application and stop the server
                    applicationContext.close();
                });

        //Start the shutdown thread
        shutdownThread.start();

        //Return HTTP 202 because the shutdown request was accepted
        return ResponseEntity
                .status(202)
                .body(response);
    }
}