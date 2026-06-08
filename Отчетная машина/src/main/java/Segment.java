/**
 * A single timed segment from the markdown file: start timecode, end timecode, and a comment.
 */
public class Segment {

    private final Timecode start;
    private final Timecode end;
    private final String comment;

    public Segment(Timecode start, Timecode end, String comment) {
        this.start = start;
        this.end = end;
        this.comment = comment;
    }

    /** Start timecode after applying the playback speed multiplier. */
    public Timecode getAdjustedStart(double speed) {
        return start.multiply(speed);
    }

    /**
     * Duration of the segment in the adjusted (sped-up) timeline.
     * Calculated as (end × speed) − (start × speed), both truncated independently.
     */
    public Timecode getAdjustedDuration(double speed) {
        return end.multiply(speed).subtract(start.multiply(speed));
    }

    public Timecode getStart()   { return start; }
    public Timecode getEnd()     { return end; }
    public String   getComment() { return comment; }
}
