package comp3011;

import java.io.File;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

public class AudioExtractor {

    public File extract(File videoFile) throws Exception {
        File audioFile =
                File.createTempFile("subtitle-audio-", ".mp3");

        audioFile.deleteOnExit();

        try (FFmpegFrameGrabber grabber =
                new FFmpegFrameGrabber(videoFile)) {

            grabber.start();

            try (FFmpegFrameRecorder recorder =
                    new FFmpegFrameRecorder(audioFile, 1)) {

                recorder.setFormat("mp3");
                recorder.setAudioCodec(
                        avcodec.AV_CODEC_ID_MP3);
                recorder.setSampleRate(16000);
                recorder.setAudioChannels(1);
                recorder.setAudioBitrate(64000);

                recorder.start();

                Frame frame;

                while ((frame = grabber.grabSamples()) != null) {
                    if (frame.samples != null) {
                        recorder.recordSamples(
                                frame.sampleRate,
                                frame.audioChannels,
                                frame.samples);
                    }
                }

                recorder.stop();
            }

            grabber.stop();
        }

        return audioFile;
    }
}