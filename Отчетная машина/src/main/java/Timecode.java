/**
 * Represents a SMPTE-style timecode in the format HH:MM:SS:FF.
 * All arithmetic is done in total frames to avoid rounding errors.
 */
public class Timecode {

    private final int hours;
    private final int minutes;
    private final int seconds;
    private final int frames;
    private final int fps;

    public Timecode(int hours, int minutes, int seconds, int frames, int fps) {
        this.hours = hours;
        this.minutes = minutes;
        this.seconds = seconds;
        this.frames = frames;
        this.fps = fps;
    }

    /**
     * Parses a timecode string in "HH:MM:SS:FF" format.
     *
     * @throws IllegalArgumentException if the format is invalid
     */
    public static Timecode parse(String s, int fps) {
        String[] parts = s.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Expected HH:MM:SS:FF format, got: " + s);
        }
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int sec = Integer.parseInt(parts[2]);
            int f = Integer.parseInt(parts[3]);
            if (m >= 60 || sec >= 60 || f >= fps) {
                throw new IllegalArgumentException(
                    "Timecode values out of range (fps=" + fps + "): " + s);
            }
            return new Timecode(h, m, sec, f, fps);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Non-numeric timecode component in: " + s, e);
        }
    }

    /** Converts to the total frame count from 00:00:00:00. */
    public int toTotalFrames() {
        return ((hours * 3600 + minutes * 60 + seconds) * fps) + frames;
    }

    /** Reconstructs a Timecode from an absolute frame count. */
    public static Timecode fromTotalFrames(int totalFrames, int fps) {
        if (totalFrames < 0) totalFrames = 0;
        int f = totalFrames % fps;
        int totalSec = totalFrames / fps;
        int sec = totalSec % 60;
        int totalMin = totalSec / 60;
        int min = totalMin % 60;
        int hrs = totalMin / 60;
        return new Timecode(hrs, min, sec, f, fps);
    }

    /**
     * Multiplies this timecode by a speed factor.
     * Frames are truncated (floor) after multiplication.
     */
    public Timecode multiply(double speed) {
        int newTotal = (int) (toTotalFrames() * speed); // explicit floor via cast
        return fromTotalFrames(newTotal, fps);
    }

    /**
     * Subtracts another timecode from this one.
     * Returns 00:00:00:00 if the result would be negative.
     */
    public Timecode subtract(Timecode other) {
        int diff = this.toTotalFrames() - other.toTotalFrames();
        return fromTotalFrames(Math.max(diff, 0), fps);
    }

    @Override
    public String toString() {
        return String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, frames);
    }

    public int getFps() {
        return fps;
    }
}
