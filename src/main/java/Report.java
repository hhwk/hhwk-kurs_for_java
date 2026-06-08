import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One report section from the markdown file (everything under a single ### heading).
 */
public class Report {

    private final String name;
    private final List<Segment> segments = new ArrayList<>();

    public Report(String name) {
        this.name = name;
    }

    public void addSegment(Segment segment) {
        segments.add(segment);
    }

    public String getName() {
        return name;
    }

    public List<Segment> getSegments() {
        return Collections.unmodifiableList(segments);
    }
}
