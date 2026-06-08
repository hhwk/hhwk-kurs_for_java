import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entry point.
 *
 * Usage:
 *   java -jar app.jar <filename_without_extension> <speed>
 *
 * Example:
 *   java -jar app.jar FIRE_FORCE_S2_EP2 1.5
 *
 * The program:
 *  1. Reads config.properties.
 *  2. Locates the Excel file (excel.files.dir/<filename>.xlsx).
 *  3. Scans all .md files in md.files.dir for a section whose name matches
 *     the episode/speed extracted from the filename and speed argument.
 *     E.g. FIRE_FORCE_S2_EP2 + 1.5  →  "S2 E2 X1.5"
 *  4. Applies the speed multiplier to all timecodes and writes the result
 *     to the Excel file starting from default.start.row.
 */
public class Main {

    public static void main(String[] args) {
        // ── Validate arguments ──────────────────────────────────────────────
        if (args.length != 2) {
            System.err.println("Ошибка: неверное количество аргументов.");
            System.err.println("Использование: java -jar app.jar <имя_файла> <скорость>");
            System.err.println("Пример:        java -jar app.jar FIRE_FORCE_S2_EP2 1.5");
            System.exit(1);
        }

        String filename = args[0];
        double speed = parseSpeed(args[1]);

        // ── Load config ─────────────────────────────────────────────────────
        Config config = loadConfig();

        // ── Find Excel file ─────────────────────────────────────────────────
        File excelDir = new File(config.getExcelFilesDir());
        File excelFile = findExcelFile(excelDir, filename);

        // ── Find the matching report ─────────────────────────────────────────
        // Priority 1: MD file with the same name as the Excel file  →  flat (film) format
        // Priority 2: Section "### …" inside any MD file            →  sectioned (series) format
        File mdDir = new File(config.getMdFilesDir());
        MarkdownParser parser = new MarkdownParser(config.getFps());
        Report report = null;

        File directMd = caseInsensitiveFind(mdDir, filename + ".md");
        if (directMd != null) {
            System.out.println("Найден файл: " + directMd.getName() + " (режим фильма — без секций)");
            try {
                report = parser.parseAsReport(directMd);
            } catch (IOException e) {
                die("Не удалось прочитать " + directMd.getName() + ": " + e.getMessage());
            }
        } else {
            // Fall back to section search inside any MD file
            String expectedSection = buildSectionName(filename, speed);
            System.out.println("MD-файл \"" + filename + ".md\" не найден, ищем секцию: \""
                + expectedSection + "\"");
            report = findReport(mdDir, expectedSection, parser);
        }

        // ── Update Excel ────────────────────────────────────────────────────
        System.out.println("Обновляем: " + excelFile.getAbsolutePath());
        ExcelUpdater updater = new ExcelUpdater();
        try {
            updater.update(excelFile, report, speed, config);
        } catch (IOException e) {
            die("Ошибка при записи Excel-файла: " + e.getMessage());
        }

        System.out.println("Готово.");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static double parseSpeed(String raw) {
        try {
            double v = Double.parseDouble(raw);
            if (v <= 0) throw new NumberFormatException("speed must be > 0");
            return v;
        } catch (NumberFormatException e) {
            die("Неверный формат скорости: \"" + raw + "\". Ожидается положительное число (например, 1.5)");
            return 0; // unreachable
        }
    }

    private static Config loadConfig() {
        try {
            return new Config();
        } catch (IOException e) {
            die("Не удалось загрузить config.properties: " + e.getMessage());
            return null; // unreachable
        }
    }

    private static File findExcelFile(File dir, String basename) {
        if (!dir.isDirectory()) {
            die("Директория excel.files.dir не найдена: " + dir.getAbsolutePath());
        }
        for (String ext : new String[]{".xlsx", ".xls"}) {
            File f = caseInsensitiveFind(dir, basename + ext);
            if (f != null) return f;
        }
        die("Excel-файл \"" + basename + ".xlsx\" не найден в " + dir.getAbsolutePath());
        return null; // unreachable
    }

    private static File caseInsensitiveFind(File dir, String name) {
        File direct = new File(dir, name);
        if (direct.exists()) return direct;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().equalsIgnoreCase(name)) return f;
            }
        }
        return null;
    }

    /**
     * Constructs the expected markdown section header from the filename and speed.
     *
     * Extraction rules:
     *   - Season:  first occurrence of S followed by digits  (e.g. S2)
     *   - Episode: first occurrence of EP followed by digits (e.g. EP2 → E2)
     *   - Speed:   formatted without trailing ".0"            (e.g. 1.5)
     *
     * Example: FIRE_FORCE_S2_EP2 + 1.5  →  "S2 E2 X1.5"
     */
    private static String buildSectionName(String filename, double speed) {
        Pattern sPattern = Pattern.compile("S(\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern ePattern = Pattern.compile("EP(\\d+)", Pattern.CASE_INSENSITIVE);

        Matcher sm = sPattern.matcher(filename);
        Matcher em = ePattern.matcher(filename);

        if (sm.find() && em.find()) {
            String season  = "S" + sm.group(1);
            String episode = "E" + em.group(1);
            String spd     = formatSpeed(speed);
            return season + " " + episode + " X" + spd;
        }

        // Fallback: use the raw filename as the section name
        System.out.println("Предупреждение: не удалось извлечь сезон/эпизод из \"" + filename
            + "\". Ищем секцию с точным именем файла.");
        return filename;
    }

    private static String formatSpeed(double speed) {
        if (speed == Math.floor(speed) && !Double.isInfinite(speed)) {
            return String.valueOf((long) speed);
        }
        // Remove unnecessary trailing zeros (e.g. 1.50 → "1.5")
        String s = Double.toString(speed);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    /**
     * Searches every .md file in mdDir for a section (### header) matching the expected name.
     * Comparison is case-insensitive.
     */
    private static Report findReport(File mdDir, String expectedSection, MarkdownParser parser) {
        if (!mdDir.isDirectory()) {
            die("Директория md.files.dir не найдена: " + mdDir.getAbsolutePath());
        }

        File[] mdFiles = mdDir.listFiles((d, name) -> name.toLowerCase().endsWith(".md"));
        if (mdFiles == null || mdFiles.length == 0) {
            die("В директории " + mdDir.getAbsolutePath() + " не найдено .md файлов");
        }

        // Sort for deterministic order
        Arrays.sort(mdFiles);

        for (File mdFile : mdFiles) {
            List<Report> reports;
            try {
                reports = parser.parse(mdFile);
            } catch (IOException e) {
                System.err.println("Предупреждение: не удалось прочитать " + mdFile.getName()
                    + ": " + e.getMessage());
                continue;
            }

            for (Report report : reports) {
                if (report.getName().equalsIgnoreCase(expectedSection)) {
                    System.out.println("Найдена секция \"" + report.getName()
                        + "\" в файле " + mdFile.getName()
                        + " (" + report.getSegments().size() + " сегментов)");
                    return report;
                }
            }
        }

        die("Секция \"" + expectedSection + "\" не найдена ни в одном .md файле в "
            + mdDir.getAbsolutePath());
        return null; // unreachable
    }

    private static void die(String message) {
        System.err.println("Ошибка: " + message);
        System.exit(1);
    }
}
