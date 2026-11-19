package comp3011.assignment1;

import java.util.concurrent.atomic.DoubleAdder;

import org.springframework.stereotype.Service;

//Stores the total amount of audio processed by the subtitle transcription service.
//The value is measured in seconds as required by the Assignment 3 usage endpoint.
@Service
public class SubtitleUsageService {

    //DoubleAdder is thread-safe, so multiple subtitle requests can update
    //the total usage without interfering with each other.
    private final DoubleAdder usage = new DoubleAdder();

    //Add the duration of one successful transcription request.
    public void addUsage(double seconds) {
        //Ignore invalid or empty duration values.
        if (seconds > 0) {
            usage.add(seconds);
        }
    }

    //Return the total transcription usage accumulated since the server started.
    public double getUsage() {
        return usage.sum();
    }
}