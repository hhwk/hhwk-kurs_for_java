import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Loads and provides access to application settings from config.properties.
 * Looks in the current directory first, then falls back to the embedded classpath resource.
 */
public class Config {

    private final Properties props = new Properties();

    public Config() throws IOException {
        File localFile = new File("config.properties");
        if (localFile.exists()) {
            try (InputStream in = new FileInputStream(localFile)) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } else {
            try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
                if (in == null) {
                    throw new IOException("config.properties not found in current directory or classpath");
                }
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        }
    }

    public String getMdFilesDir() {
        return props.getProperty("md.files.dir", "./md_files");
    }

    public String getExcelFilesDir() {
        return props.getProperty("excel.files.dir", "./excel_files");
    }

    public String getDefaultSheet() {
        return props.getProperty("default.sheet", "Лист1");
    }

    public int getDefaultStartRow() {
        return Integer.parseInt(props.getProperty("default.start.row", "13"));
    }

    public boolean isClearExistingData() {
        return Boolean.parseBoolean(props.getProperty("clear.existing.data", "true"));
    }

    /** Frames per second used for timecode calculations. */
    public int getFps() {
        return Integer.parseInt(props.getProperty("fps", "25"));
    }
}
