package mas.mail.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * settings of the app, read from {@code config/app.properties}
 * values can be changed on the command line with {@code --set key=value}
 */
public final class AppConfig {

    private final Path baseDir;
    private final Properties props;

    private AppConfig(Path baseDir, Properties props) {
        this.baseDir = baseDir;
        this.props = props;
    }

    public static AppConfig load(Path baseDir, Path configFile) {
        Properties p = new Properties();
        if (Files.exists(configFile)) {
            try (Reader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + configFile, e);
            }
        }
        return new AppConfig(baseDir, p);
    }

    public void set(String key, String value) {
        props.setProperty(key, value);
    }

    public String get(String key, String def) {
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? def : v.trim();
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public double getDouble(String key, double def) {
        try {
            return Double.parseDouble(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(get(key, String.valueOf(def)));
    }

    public Path baseDir() {
        return baseDir;
    }

    /** turns a path setting into a full path, starting from the base folder */
    public Path path(String key, String def) {
        Path p = Path.of(get(key, def));
        return p.isAbsolute() ? p : baseDir.resolve(p);
    }

    public Path dataDir() {
        return path("paths.data", "data");
    }
}
