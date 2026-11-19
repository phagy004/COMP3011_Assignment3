package comp3011.assignment1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Assignment1Application {

    //Main method used to start the Spring Boot application
    public static void main(String[] args) {

        //Start Spring and run the web server
        SpringApplication.run(
                Assignment1Application.class,
                args
        );
    }
}