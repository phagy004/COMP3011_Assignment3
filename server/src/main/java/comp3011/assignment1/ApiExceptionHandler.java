package comp3011.assignment1;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

    //Handle errors that were not already handled inside a controller
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleException(
	        Exception error,
	        HttpServletRequest request) {

	    error.printStackTrace();

	    Map<String, Object> response = new HashMap<>();

	    response.put(
	            "timestamp",
	            Instant.now().toString()
	    );

	    response.put(
	            "status",
	            500
	    );

	    response.put(
	            "error",
	            "Internal Server Error"
	    );

	    response.put(
	            "message",
	            "An unexpected server error occurred."
	    );

	    response.put(
	            "path",
	            request.getRequestURI()
	    );

	    return ResponseEntity
	            .status(500)
	            .body(response);
	}
}