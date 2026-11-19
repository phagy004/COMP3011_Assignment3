package comp3011.assignment1;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class StatisticsService {

    //Store the total number of input tokens
    //AtomicLong is used because many requests can update this value at the same time
    private final AtomicLong inputTokens =
            new AtomicLong(0);

    //Store the total number of output tokens
    //AtomicLong helps stop values from being overwritten by concurrent requests
    private final AtomicLong outputTokens =
            new AtomicLong(0);

    public void addTokens(long input, long output) {

        //Add the input tokens from one transcription request
        inputTokens.addAndGet(input);

        //Add the output tokens from one transcription request
        outputTokens.addAndGet(output);
    }

    public long getInputTokens() {

        //Return the current total input token value
        return inputTokens.get();
    }

    public long getOutputTokens() {

        //Return the current total output token value
        return outputTokens.get();
    }
}