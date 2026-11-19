package comp3011.assignment1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class AdminControllerTests {

    @Test
    public void testUptimeResponse() {

        //Create the controller directly for a simple unit test
        AdminController controller =
                new AdminController();

        //Call the uptime method
        ResponseEntity<Map<String, Object>> response =
                controller.getServerUptime();

        //Check that the request returns HTTP 200
        assertEquals(
                200,
                response.getStatusCode().value()
        );

        //Get the response body
        Map<String, Object> body =
                response.getBody();

        //Check that a response body was returned
        assertNotNull(body);

        //Check that the server start time exists
        assertNotNull(
                body.get("utcServerStart")
        );

        //Check that the current server time exists
        assertNotNull(
                body.get("utcNow")
        );

        //Check that the uptime value exists
        assertNotNull(
                body.get("serverUptimeSeconds")
        );
    }
}