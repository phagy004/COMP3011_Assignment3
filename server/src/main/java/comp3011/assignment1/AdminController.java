package comp3011.assignment1;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    //Store the time when the server starts
    //This value stays the same until the application is restarted
    private final Instant serverStartTime;

    public AdminController() {

        //Save the current time when this controller is created
        serverStartTime =
                Instant.now();
    }

    //Return information about how long the server has been running
    @GetMapping("/api/v1/admin/uptime")
    public ResponseEntity<Map<String, Object>> getServerUptime() {

        //Get the current time when this request is received
        Instant now =
                Instant.now();

        //Calculate the difference between the start time and current time
        //The result is converted from milliseconds into seconds
        double uptimeSeconds =
                Duration
                        .between(
                                serverStartTime,
                                now
                        )
                        .toMillis()
                        / 1000.0;

        //Store the values that will be returned as JSON
        Map<String, Object> response =
                new HashMap<>();

        //Return the time when the server started
        response.put(
                "utcServerStart",
                serverStartTime.toString()
        );

        //Return the current server time
        response.put(
                "utcNow",
                now.toString()
        );

        //Return how many seconds the server has been running
        response.put(
                "serverUptimeSeconds",
                uptimeSeconds
        );

        //Return HTTP 200 with the uptime information
        return ResponseEntity
                .ok(response);
    }
}