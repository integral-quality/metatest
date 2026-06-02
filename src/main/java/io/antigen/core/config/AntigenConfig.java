package io.antigen.core.config;

import lombok.Data;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Data
public class AntigenConfig {
    private static AntigenConfig instance;
    private final String apiKey;
    private final String apiBaseUrl;
    private final String projectId;

    private AntigenConfig() {
        Properties props = loadProperties();
        this.apiKey = getConfigValue(props, "antigen.api.key", "ANTIGEN_API_KEY");
        this.apiBaseUrl = getConfigValue(props, "antigen.api.url", "ANTIGEN_API_URL", "http://localhost:8080");
        this.projectId = getConfigValue(props, "antigen.project.id", "ANTIGEN_PROJECT_ID");
    }

    public static synchronized AntigenConfig getInstance() {
        if (instance == null) {
            instance = new AntigenConfig();
        }
        return instance;
    }

    // For testing purposes - allows resetting the singleton
    public static synchronized void resetInstance() {
        instance = null;
    }

    private Properties loadProperties() {
        Properties props = new Properties();

        String[] candidates = {"antigen/antigen.properties", "antigen.properties"};
        for (String path : candidates) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    props.load(is);
                    System.out.println("[Antigen] Loaded configuration from " + path);
                    break;
                }
            } catch (IOException e) {
                System.out.println("[Antigen] Could not load " + path + ": " + e.getMessage());
            }
        }

        return props;
    }

    private String getConfigValue(Properties props, String propertyName, String envVarName) {
        return getConfigValue(props, propertyName, envVarName, null);
    }

    private String getConfigValue(Properties props, String propertyName, String envVarName, String defaultValue) {
        // Priority: System property > Environment variable > Properties file > Default value
        String value = System.getProperty(propertyName);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        value = System.getenv(envVarName);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        value = props.getProperty(propertyName);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        return defaultValue;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public boolean hasProjectId() {
        return projectId != null && !projectId.isEmpty();
    }

    public boolean isApiConfigured() {
        return hasApiKey() && hasProjectId();
    }

    public String getFaultStrategiesUrl() {
        if (!isApiConfigured()) {
            throw new IllegalStateException("API is not configured. Cannot get fault strategies URL.");
        }
        return apiBaseUrl + "/api/v1/projects/" + projectId + "/fault-strategies/contract";
    }

    public String getSimulationResultsUrl() {
        if (!isApiConfigured()) {
            throw new IllegalStateException("API is not configured. Cannot get simulation results URL.");
        }
        return apiBaseUrl + "/api/v1/projects/" + projectId + "/simulation-results";
    }

    @Override
    public String toString() {
        return "AntigenConfig{" +
                "apiBaseUrl='" + apiBaseUrl + '\'' +
                ", projectId='" + projectId + '\'' +
                ", apiKey='" + (apiKey != null ? "***CONFIGURED***" : "NOT_SET") + '\'' +
                '}';
    }
}
