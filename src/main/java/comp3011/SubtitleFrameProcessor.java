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

public class SubtitleFrameProcessor extends FrameProcessor {

    private static final int MAX_CHARACTERS_PER_LINE = 42;
    private static final int MAX_LINES = 2;

    private volatile boolean enabled = true;
    private volatile long mediaStartTimestampUs = Long.MIN_VALUE;
    private volatile List<SubtitleSegment> subtitles = List.of();

    public SubtitleFrameProcessor() {
        super();
    }

    @Override
    public void process(Frame frame, InfoFrame info) throws Exception {

        if (mediaStartTimestampUs == Long.MIN_VALUE) {
            mediaStartTimestampUs = info.timestampUs();
        }

        if (!enabled) {
            return;
        }

        SubtitleSegment subtitle = findSubtitle(info.timestampUs());

        if (subtitle == null) {
            return;
        }

        drawSubtitle(frame, subtitle.getText());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setSubtitles(List<SubtitleSegment> subtitles) {
        if (subtitles == null) {
            this.subtitles = List.of();
            return;
        }

        //copy the list so another thread cannot change it
        this.subtitles = List.copyOf(subtitles);
    }

    public SubtitleSegment findSubtitle(long timestampUs) {

        long startTimestamp = mediaStartTimestampUs;

        double timeSeconds;

        if (startTimestamp == Long.MIN_VALUE) {
            timeSeconds = 0;
        } else {
            timeSeconds =
                    Math.max(0, timestampUs - startTimestamp)
                    / 1_000_000.0;
        }

        for (SubtitleSegment subtitle : subtitles) {
            if (subtitle.isVisibleAt(timeSeconds)) {
                return subtitle;
            }
        }

        return null;
    }

    private void drawSubtitle(Frame frame, String text) throws Exception {
        if (frame.image == null || frame.image.length == 0 || frame.image[0] == null) {
            return;
        }

        if (!(frame.image[0] instanceof ByteBuffer pixels)) {
            throw new Exception("SubtitleFrameProcessor requires byte-backed image frames");
        }

        if (videoInfo.imageDepth() != Frame.DEPTH_UBYTE && videoInfo.imageDepth() != Frame.DEPTH_BYTE) {
            throw new Exception("SubtitleFrameProcessor requires 8-bit image frames");
        }

        if (videoInfo.imageChannels() < 3) {
            throw new Exception("SubtitleFrameProcessor requires at least three image channels");
        }

        List<String> lines = splitSubtitle(text);

        if (lines.isEmpty()) {
            return;
        }

        int width = videoInfo.imageWidth();
        int height = videoInfo.imageHeight();
        int fontSize = Math.max(20, Math.min(44, height / 18));
        int overlayHeight = fontSize * 3;

        Font font = new Font("SansSerif", Font.BOLD, fontSize);
        BufferedImage overlay = new BufferedImage(width, overlayHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = overlay.createGraphics();

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setFont(font);

        FontMetrics metrics = graphics.getFontMetrics();
        int lineHeight = metrics.getHeight();
        int totalHeight = lineHeight * lines.size();
        int startY = (overlayHeight - totalHeight) / 2 + metrics.getAscent();
        float outlineWidth = Math.max(2.0f, fontSize / 12.0f);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            int textWidth = metrics.stringWidth(line);
            float x = Math.max(0, (width - textWidth) / 2.0f);
            float y = startY + i * lineHeight;

            GlyphVector glyphs = font.createGlyphVector(graphics.getFontRenderContext(), line);
            Shape textShape = glyphs.getOutline(x, y);

            //draw black outline
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(textShape);

            //draw white text
            graphics.setColor(Color.WHITE);
            graphics.fill(textShape);
        }

        graphics.dispose();

        int bottomMargin = Math.max(20, height / 18);
        int overlayY = Math.max(0, height - overlayHeight - bottomMargin);

        copyOverlayToFrame(pixels, overlay, overlayY);
    }

    private List<String> splitSubtitle(String text) {
        List<String> lines = new ArrayList<>();

        if (text == null) {
            return lines;
        }

        String remaining = text.trim().replaceAll("\\s+", " ");

        while (!remaining.isEmpty() && lines.size() < MAX_LINES) {
            if (remaining.length() <= MAX_CHARACTERS_PER_LINE) {
                lines.add(remaining);
                break;
            }

            int split = findSplitPosition(remaining);
            lines.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }

        if (lines.size() == MAX_LINES) {
            int lastIndex = lines.size() - 1;
            String lastLine = lines.get(lastIndex);

            if (lastLine.length() > MAX_CHARACTERS_PER_LINE) {
                lines.set(lastIndex, lastLine.substring(0, MAX_CHARACTERS_PER_LINE));
            }
        }

        return lines;
    }

    private int findSplitPosition(String text) {
        int limit = Math.min(MAX_CHARACTERS_PER_LINE, text.length());

        //try punctuation first
        for (int i = limit - 1; i >= 1; i--) {
            char character = text.charAt(i);

            if (character == ',' || character == '.' || character == '!'
                    || character == '?' || character == ';' || character == ':') {
                return i + 1;
            }
        }

        //otherwise use the last space
        for (int i = limit; i >= 1; i--) {
            if (text.charAt(i - 1) == ' ') {
                return i;
            }
        }

        return limit;
    }

    private void copyOverlayToFrame(ByteBuffer pixels, BufferedImage overlay, int overlayY) {
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

            for (int x = 0; x < overlay.getWidth() && x < frameWidth; x++) {
                int argb = overlay.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;

                if (alpha == 0) {
                    continue;
                }

                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                int base = rowStart + x * channels;

                //JavaCV stores image colour in BGR order
                blendChannel(pixels, base, blue, alpha);
                blendChannel(pixels, base + 1, green, alpha);
                blendChannel(pixels, base + 2, red, alpha);
            }
        }
    }

    private void blendChannel(ByteBuffer pixels, int index, int overlayValue, int alpha) {
        int originalValue = Byte.toUnsignedInt(pixels.get(index));
        int result = (overlayValue * alpha + originalValue * (255 - alpha)) / 255;

        pixels.put(index, (byte) result);
    }
    
    public void resetTiming() {
        mediaStartTimestampUs = Long.MIN_VALUE;
    }
}