package com.inlearn.config;

import com.inlearn.exception.ConfigurationException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application settings loaded from a properties file, with database values optionally
 * overridden by environment variables so passwords never need to live in source control.
 *
 * <p>Lookup order for the file: {@code -Dinlearn.config}, then {@code INLEARN_CONFIG},
 * then {@code config/application.properties} in the working directory.
 */
public final class AppConfig {

    public static final String CONFIG_SYSTEM_PROPERTY = "inlearn.config";
    public static final String CONFIG_ENVIRONMENT_VARIABLE = "INLEARN_CONFIG";
    public static final Path DEFAULT_CONFIG_PATH = Path.of("config", "application.properties");

    private static final String DEFAULT_STORAGE_DIRECTORY = "./inlearn-data/storage";
    private static final String DEFAULT_LOGGING_DIRECTORY = "./inlearn-data/logs";
    private static final int DEFAULT_MAX_UPLOAD_MEGABYTES = 25;
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 8;
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;
    private static final String DEFAULT_ALLOWED_EXTENSIONS =
            "pdf,doc,docx,odt,rtf,txt,md,ppt,pptx,odp,xls,xlsx,ods,csv,zip,png,jpg,jpeg,gif,"
                    + "java,py,c,cpp,h,js,html,css,sql,json,xml";

    /** Environment variables that take priority over the matching file property. */
    private static final Map<String, String> ENVIRONMENT_OVERRIDES = Map.of(
            "db.url", "INLEARN_DB_URL",
            "db.username", "INLEARN_DB_USERNAME",
            "db.password", "INLEARN_DB_PASSWORD");

    private final Properties properties;
    private final Map<String, String> environment;
    private final String sourceDescription;

    private AppConfig(Properties properties, Map<String, String> environment, String sourceDescription) {
        this.properties = properties;
        this.environment = environment;
        this.sourceDescription = sourceDescription;
    }

    /** Loads the configuration file using the documented lookup order. */
    public static AppConfig load() {
        Path configPath = resolveConfigPath();
        if (!Files.isRegularFile(configPath)) {
            throw new ConfigurationException("InLearn could not find its configuration file at "
                    + configPath.toAbsolutePath().normalize() + ". Copy config/application.example.properties "
                    + "to config/application.properties and enter your database settings.");
        }
        Properties loaded = new Properties();
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            loaded.load(reader);
        } catch (IOException e) {
            throw new ConfigurationException("InLearn could not read its configuration file at "
                    + configPath.toAbsolutePath().normalize() + ".", e);
        }
        return new AppConfig(loaded, System.getenv(), configPath.toAbsolutePath().normalize().toString());
    }

    /** Creates a configuration from in-memory properties (used by tests and tools). */
    public static AppConfig fromProperties(Properties properties) {
        return new AppConfig(properties, Map.of(), "in-memory properties");
    }

    private static Path resolveConfigPath() {
        String fromSystemProperty = System.getProperty(CONFIG_SYSTEM_PROPERTY);
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return Path.of(fromSystemProperty.trim());
        }
        String fromEnvironment = System.getenv(CONFIG_ENVIRONMENT_VARIABLE);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return Path.of(fromEnvironment.trim());
        }
        return DEFAULT_CONFIG_PATH;
    }

    public String sourceDescription() {
        return sourceDescription;
    }

    public String databaseUrl() {
        return requireValue("db.url");
    }

    public String databaseUsername() {
        return requireValue("db.username");
    }

    public String databasePassword() {
        String password = value("db.password");
        return password == null ? "" : password;
    }

    public int databaseConnectTimeoutSeconds() {
        return positiveInt("db.connectTimeoutSeconds", DEFAULT_CONNECT_TIMEOUT_SECONDS);
    }

    public Path storageDirectory() {
        String directory = value("storage.directory");
        return Path.of(isBlank(directory) ? DEFAULT_STORAGE_DIRECTORY : directory.trim()).toAbsolutePath().normalize();
    }

    public long maxUploadBytes() {
        return positiveInt("storage.maxUploadMegabytes", DEFAULT_MAX_UPLOAD_MEGABYTES) * BYTES_PER_MEGABYTE;
    }

    public Set<String> allowedFileExtensions() {
        String configured = value("storage.allowedExtensions");
        String list = isBlank(configured) ? DEFAULT_ALLOWED_EXTENSIONS : configured;
        Set<String> extensions = Arrays.stream(list.split(","))
                .map(extension -> extension.trim().toLowerCase(Locale.ROOT))
                .map(extension -> extension.startsWith(".") ? extension.substring(1) : extension)
                .filter(extension -> !extension.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(extensions);
    }

    /** The zone used to display and enter deadlines; defaults to the computer's zone. */
    public ZoneId displayZone() {
        String zone = value("app.timezone");
        if (isBlank(zone)) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(zone.trim());
        } catch (DateTimeException e) {
            throw new ConfigurationException("The app.timezone setting \"" + zone
                    + "\" is not a valid time zone. Use a name such as America/Toronto.", e);
        }
    }

    /**
     * "development" (the default) or "production". Development-only tools such as the demo data
     * seeder refuse to run against a production configuration.
     */
    public boolean isProduction() {
        String environment = value("app.environment");
        return environment != null && environment.trim().equalsIgnoreCase("production");
    }

    public Path loggingDirectory() {
        String directory = value("logging.directory");
        return Path.of(isBlank(directory) ? DEFAULT_LOGGING_DIRECTORY : directory.trim()).toAbsolutePath().normalize();
    }

    private String value(String key) {
        String environmentVariable = ENVIRONMENT_OVERRIDES.get(key);
        if (environmentVariable != null) {
            String fromEnvironment = environment.get(environmentVariable);
            if (!isBlank(fromEnvironment)) {
                return fromEnvironment;
            }
        }
        return properties.getProperty(key);
    }

    private String requireValue(String key) {
        String value = value(key);
        if (isBlank(value)) {
            throw new ConfigurationException("The setting \"" + key + "\" is missing from " + sourceDescription + ".");
        }
        return value.trim();
    }

    private int positiveInt(String key, int defaultValue) {
        String value = value(key);
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                throw new NumberFormatException("must be positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ConfigurationException("The setting \"" + key + "\" must be a whole number greater than zero.", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
