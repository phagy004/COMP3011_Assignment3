package comp3011;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.bytedeco.javacv.Frame;

//Frame processor responsible for displaying subtitles on top of video frames.
//It follows the same processing pipeline as the other Assignment 2 effects.
public class SubtitleFrameProcessor extends FrameProcessor {

    //Assignment requirements limit each subtitle to 42 characters
    //per line and no more than two lines.
    private static final int MAX_CHARACTERS_PER_LINE = 42;
    private static final int MAX_LINES = 2;

    //Volatile is used because the controller and frame processing workers
    //may access this state from different threads.
    private volatile boolean enabled = true;

    //The subtitle list can be replaced after the server finishes transcription.
    private volatile List<SubtitleSegment> subtitles = List.of();

    public SubtitleFrameProcessor() {
        super();
    }

    @Override
    public void process(Frame frame, InfoFrame info) throws Exception {
        //When subtitles are disabled the frame is returned unchanged.
        if (!enabled) {
            return;
        }

        //Find the subtitle whose Whisper time range contains the current
        //logical playback timestamp.
        SubtitleSegment subtitle =
                findSubtitle(info.playbackTimestampUs());

        if (subtitle == null) {
            return;
        }

        //Draw the subtitle directly into the video frame before it is displayed.
        drawSubtitle(frame, subtitle.getText());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    //Called after SubtitleService receives the server response.
    public void setSubtitles(List<SubtitleSegment> subtitles) {
        if (subtitles == null) {
            this.subtitles = List.of();
            return;
        }

        //Use an immutable copy so another thread cannot modify the list
        //while a worker is searching it.
        this.subtitles = List.copyOf(subtitles);
    }

    public SubtitleSegment findSubtitle(long playbackTimestampUs) {
        //VideoPlayerModel stores timestamps in microseconds,
        //while Whisper timestamps are measured in seconds.
        double timeSeconds =
                playbackTimestampUs / 1_000_000.0;

        //Search from newest to oldest so a newer subtitle is selected
        //if two subtitle display ranges briefly overlap.
        for (int i = subtitles.size() - 1; i >= 0; i--) {
            SubtitleSegment subtitle = subtitles.get(i);

            if (subtitle.isVisibleAt(timeSeconds)) {
                return subtitle;
            }
        }

        return null;
    }

    //Draw subtitle text using white sans-serif text and a black outline.
    //The outline improves readability on both light and dark video scenes.
    private void drawSubtitle(Frame frame, String text) throws Exception {
        if (frame.image == null || frame.image.length == 0 || frame.image[0] == null) {
            return;
        }

        //The drawing code works directly with byte-backed JavaCV frames.
        if (!(frame.image[0] instanceof ByteBuffer pixels)) {
            throw new Exception(
                    "SubtitleFrameProcessor requires byte-backed image frames");
        }

        if (videoInfo.imageDepth() != Frame.DEPTH_UBYTE
                && videoInfo.imageDepth() != Frame.DEPTH_BYTE) {
            throw new Exception(
                    "SubtitleFrameProcessor requires 8-bit image frames");
        }

        if (videoInfo.imageChannels() < 3) {
            throw new Exception(
                    "SubtitleFrameProcessor requires at least three image channels");
        }

        //Split a long subtitle before drawing it.
        List<String> lines = splitSubtitle(text);

        if (lines.isEmpty()) {
            return;
        }

        int width = videoInfo.imageWidth();
        int height = videoInfo.imageHeight();

        //Scale the subtitle font with the video resolution.
        int fontSize =
                Math.max(
                        20,
                        Math.min(
                                44,
                                height / 18));

        int overlayHeight = fontSize * 3;

        Font font =
                new Font(
                        "SansSerif",
                        Font.BOLD,
                        fontSize);

        //Text is first drawn onto a transparent overlay.
        BufferedImage overlay =
                new BufferedImage(
                        width,
                        overlayHeight,
                        BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = overlay.createGraphics();

        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.setFont(font);

        FontMetrics metrics = graphics.getFontMetrics();
        int lineHeight = metrics.getHeight();
        int totalHeight = lineHeight * lines.size();

        //Centre the complete subtitle block vertically inside the overlay.
        int startY =
                (overlayHeight - totalHeight) / 2
                + metrics.getAscent();

        float outlineWidth =
                Math.max(
                        2.0f,
                        fontSize / 12.0f);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            //Centre each subtitle line horizontally.
            int textWidth = metrics.stringWidth(line);
            float x =
                    Math.max(
                            0,
                            (width - textWidth) / 2.0f);

            float y = startY + i * lineHeight;

            GlyphVector glyphs =
                    font.createGlyphVector(
                            graphics.getFontRenderContext(),
                            line);

            Shape textShape =
                    glyphs.getOutline(x, y);

            //Draw the black outline first.
            graphics.setColor(Color.BLACK);
            graphics.setStroke(
                    new BasicStroke(
                            outlineWidth,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
            graphics.draw(textShape);

            //Fill the same text shape with white.
            graphics.setColor(Color.WHITE);
            graphics.fill(textShape);
        }

        graphics.dispose();

        //Place subtitles near the bottom of the video with a small margin.
        int bottomMargin =
                Math.max(
                        20,
                        height / 18);

        int overlayY =
                Math.max(
                        0,
                        height
                        - overlayHeight
                        - bottomMargin);

        copyOverlayToFrame(
                pixels,
                overlay,
                overlayY);
    }

    //Break long text into at most two lines.
    private List<String> splitSubtitle(String text) {
        List<String> lines = new ArrayList<>();

        if (text == null) {
            return lines;
        }

        //Remove repeated whitespace returned by transcription.
        String remaining =
                text.trim()
                        .replaceAll("\\s+", " ");

        while (!remaining.isEmpty()
                && lines.size() < MAX_LINES) {

            if (remaining.length()
                    <= MAX_CHARACTERS_PER_LINE) {

                lines.add(remaining);
                break;
            }

            int split =
                    findSplitPosition(remaining);

            lines.add(
                    remaining
                            .substring(0, split)
                            .trim());

            remaining =
                    remaining
                            .substring(split)
                            .trim();
        }

        return lines;
    }

    //Try to break after punctuation because this normally gives
    //a more natural subtitle line than breaking in the middle of a phrase.
    private int findSplitPosition(String text) {
        int limit =
                Math.min(
                        MAX_CHARACTERS_PER_LINE,
                        text.length());

        for (int i = limit - 1; i >= 1; i--) {
            char character =
                    text.charAt(i);

            if (character == ','
                    || character == '.'
                    || character == '!'
                    || character == '?'
                    || character == ';'
                    || character == ':') {

                return i + 1;
            }
        }

        //If there is no punctuation, use the last available space.
        for (int i = limit; i >= 1; i--) {
            if (text.charAt(i - 1) == ' ') {
                return i;
            }
        }

        //A very long word may not contain any spaces.
        return limit;
    }

    //Blend the transparent text overlay into the original JavaCV frame.
    private void copyOverlayToFrame(
            ByteBuffer pixels,
            BufferedImage overlay,
            int overlayY) {

        int frameWidth = videoInfo.imageWidth();
        int frameHeight = videoInfo.imageHeight();
        int channels = videoInfo.imageChannels();
        int stride = videoInfo.imageStride();

        for (int y = 0; y < overlay.getHeight(); y++) {
            int frameY = overlayY + y;

            if (frameY < 0 || frameY >= frameHeight) {
                continue;
            }

            int rowStart = frameY * stride;

            for (int x = 0;
                    x < overlay.getWidth()
                    && x < frameWidth;
                    x++) {

                int argb = overlay.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;

                //Transparent overlay pixels do not need to change the frame.
                if (alpha == 0) {
                    continue;
                }

                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                int base = rowStart + x * channels;

                //JavaCV stores the image channels in BGR order.
                blendChannel(pixels, base, blue, alpha);
                blendChannel(pixels, base + 1, green, alpha);
                blendChannel(pixels, base + 2, red, alpha);
            }
        }
    }

    //Alpha blend one colour channel from the overlay into the video frame.
    private void blendChannel(
            ByteBuffer pixels,
            int index,
            int overlayValue,
            int alpha) {

        int originalValue =
                Byte.toUnsignedInt(
                        pixels.get(index));

        int result =
                (overlayValue * alpha
                + originalValue * (255 - alpha))
                / 255;

        pixels.put(
                index,
                (byte) result);
    }
}