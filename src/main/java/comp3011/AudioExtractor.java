package comp3011;

import java.io.File;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

//Extracts only the audio track from the selected media file.
//This avoids sending the much larger original video file for transcription.
public class AudioExtractor {

    public File extract(File videoFile) throws Exception {
        //The temporary file is deleted automatically when the application exits.
        File audioFile =
                File.createTempFile("subtitle-audio-", ".mp3");

        audioFile.deleteOnExit();

        //JavaCV uses FFmpeg internally to read the media file.
        try (FFmpegFrameGrabber grabber =
                new FFmpegFrameGrabber(videoFile)) {

            grabber.start();

            try (FFmpegFrameRecorder recorder =
                    new FFmpegFrameRecorder(audioFile, 1)) {

                //Speech recognition does not require the original high quality
                //audio, so mono 16 kHz audio keeps the upload much smaller.
                recorder.setFormat("mp3");
                recorder.setAudioCodec(avcodec.AV_CODEC_ID_MP3);
                recorder.setSampleRate(16000);
                recorder.setAudioChannels(1);
                recorder.setAudioBitrate(64000);

                recorder.start();

                Frame frame;

                //Only audio sample frames are copied into the MP3.
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