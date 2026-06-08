import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a markdown file into a list of Reports.
 *
 * Supports two formats:
 *   1. Sectioned (anime/series): sections separated by "### Section Name" headers.
 *   2. Flat (films): no headers, just timecode lines from the top of the file.
 */
public class MarkdownParser {

    // Matches: "HH:MM:SS:FF HH:MM:SS:FF comment"
    // The gap between end-timecode and comment is optional (\\s*) to handle
    // malformed lines where the space is accidentally omitted.
    private static final Pattern SEGMENT_LINE = Pattern.compile(
        "^(\\d{2}:\\d{2}:\\d{2}:\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}:\\d{2})\\s*(.+)$"
    );

    private final int fps;

    public MarkdownParser(int fps) {
        this.fps = fps;
    }

    /**
     * Parses the entire file as a single report (flat format — no ### headers needed).
     * Used for film MD files where there is only one report per file.
     *
     * @param file the .md file to parse
     * @return a Report named after the file (without extension)
     * @throws IOException if the file cannot be read
     */
    public Report parseAsReport(File file) throws IOException {
        String name = file.getName().replaceFirst("(?i)\\.md$", "");
        Report report = new Report(name);

        try (BufferedReader reader = openReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                Matcher m = SEGMENT_LINE.matcher(line);
                if (!m.matches()) continue;

                try {
                    Timecode start   = Timecode.parse(m.group(1), fps);
                    Timecode end     = Timecode.parse(m.group(2), fps);
                    // Trim whitespace and stray trailing slashes
                    String comment   = m.group(3).trim().replaceAll("[/\\\\]+$", "").trim();
                    report.addSegment(new Segment(start, end, comment));
                } catch (IllegalArgumentException e) {
                    System.err.println("Предупреждение: пропущена строка в " + file.getName()
                        + ": " + line + " (" + e.getMessage() + ")");
                }
            }
        }

        return report;
    }

    /**
     * Parses the given file and returns all report sections found (sectioned format).
     * Each "### Name" header starts a new report.
     *
     * @throws IOException if the file cannot be read
     */
    public List<Report> parse(File file) throws IOException {
        List<Report> reports = new ArrayList<>();
        Report current = null;

        try (BufferedReader reader = openReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("### ")) {
                    current = new Report(line.substring(4).trim());
                    reports.add(current);
                    continue;
                }

                if (current == null || line.isEmpty()) continue;

                Matcher m = SEGMENT_LINE.matcher(line);
                if (!m.matches()) continue;

                try {
                    Timecode start = Timecode.parse(m.group(1), fps);
                    Timecode end   = Timecode.parse(m.group(2), fps);
                    String comment = m.group(3).trim().replaceAll("[/\\\\]+$", "").trim();
                    current.addSegment(new Segment(start, end, comment));
                } catch (IllegalArgumentException e) {
                    System.err.println("Предупреждение: пропущена строка в " + file.getName()
                        + ": " + line + " (" + e.getMessage() + ")");
                }
            }
        }

        return reports;
    }

    /** Opens the file as UTF-8, stripping a leading BOM if present. */
    private static BufferedReader openReader(File file) throws IOException {
        InputStream raw = new FileInputStream(file);
        // Consume UTF-8 BOM (EF BB BF) if present
        PushbackInputStream pb = new PushbackInputStream(raw, 3);
        byte[] bom = new byte[3];
        int read = pb.read(bom);
        if (read == 3 && bom[0] == (byte) 0xEF
                && bom[1] == (byte) 0xBB
                && bom[2] == (byte) 0xBF) {
            // BOM consumed — do nothing
        } else if (read > 0) {
            pb.unread(bom, 0, read);
        }
        return new BufferedReader(new InputStreamReader(pb, StandardCharsets.UTF_8));
    }
}
