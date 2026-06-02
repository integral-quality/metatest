package io.antigen.core.config;

import io.antigen.core.config.ConfigurationSource;
import io.antigen.core.config.FaultCollection;
import io.antigen.core.config.LocalConfigurationSource;
import io.antigen.core.config.AntigenConfig;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConfigurationSourceTest {

    @BeforeEach
    public void setUp() {
        System.clearProperty("antigen.config.source");
        System.clearProperty("antigen.api.key");
        System.clearProperty("antigen.project.id");
        System.clearProperty("antigen.api.url");
        AntigenConfig.resetInstance();  // Reset AFTER clearing properties
    }

    @AfterEach
    public void tearDown() {
        // Clean up after each test to prevent pollution
        System.clearProperty("antigen.config.source");
        System.clearProperty("antigen.api.key");
        System.clearProperty("antigen.project.id");
        System.clearProperty("antigen.api.url");
        AntigenConfig.resetInstance();
    }

    @Test
    @Order(1)
    public void testLocalConfigurationSource() {
        LocalConfigurationSource localConfig = new LocalConfigurationSource();

        assertEquals("Local YAML", localConfig.getSourceName());

        List<FaultCollection> enabledFaults = localConfig.getEnabledFaults();
        assertNotNull(enabledFaults);
        assertTrue(enabledFaults.contains(FaultCollection.null_field),
                "null_field should be enabled in test config.yml");
        assertTrue(enabledFaults.contains(FaultCollection.missing_field),
                "missing_field should be enabled in test config.yml");
        assertTrue(enabledFaults.contains(FaultCollection.invalid_value),
                "invalid_value should be enabled in test config.yml");
        assertFalse(enabledFaults.contains(FaultCollection.empty_list),
                "empty_list should be disabled in test config.yml");
        assertFalse(enabledFaults.contains(FaultCollection.empty_string),
                "empty_string should be disabled in test config.yml");

    }

    @Test
    @Order(2)
    public void testLocalConfigurationSourceExclusions() {
        LocalConfigurationSource localConfig = new LocalConfigurationSource();


        assertTrue(localConfig.isEndpointExcluded("/api/login"),
                "Endpoints matching */login* should be excluded");
        assertTrue(localConfig.isEndpointExcluded("/auth/validate"),
                "Endpoints matching */auth/* should be excluded");
        assertFalse(localConfig.isEndpointExcluded("/api/users"),
                "Regular endpoints should not be excluded");

        assertTrue(localConfig.isTestExcluded("UserLoginTest"),
                "Tests matching *LoginTest* should be excluded");
        assertFalse(localConfig.isTestExcluded("UserRegistrationTest"),
                "Regular tests should not be excluded");
    }

    @Test
    @Order(3)
    public void testConfigurationSourceInterface() {
        ConfigurationSource localConfig = new LocalConfigurationSource();

        assertNotNull(localConfig.getEnabledFaults());
        assertNotNull(localConfig.getSourceName());
        assertFalse(localConfig.isEndpointExcluded("/api/test"));
        assertFalse(localConfig.isTestExcluded("TestClass"));

        System.out.println("ConfigurationSource interface implemented correctly");
    }

    @Test
    @Order(4)
    public void testAntigenConfigOptionalApiKey() {
        AntigenConfig config = AntigenConfig.getInstance();

        assertNotNull(config);
        assertFalse(config.isApiConfigured(),
                "API should not be configured when no API key is present");
        assertFalse(config.hasApiKey());
        assertFalse(config.hasProjectId());

        System.out.println("AntigenConfig works without API key (for local mode)");
    }

    @Test
    @Order(6)  // Run this last since it sets API config
    public void testAntigenConfigWithApiKey() {
        System.setProperty("antigen.api.key", "mt_proj_test123");
        System.setProperty("antigen.project.id", "test-project-id");

        AntigenConfig config = AntigenConfig.getInstance();

        assertNotNull(config);
        assertTrue(config.isApiConfigured(),
                "API should be configured when API key and project ID are present");
        assertTrue(config.hasApiKey());
        assertTrue(config.hasProjectId());
        assertEquals("mt_proj_test123", config.getApiKey());
        assertEquals("test-project-id", config.getProjectId());
    }

    @Test
    @Order(5)
    public void testAntigenConfigUrlGenerationFailsWithoutApiConfig() {
        AntigenConfig config = AntigenConfig.getInstance();

        assertThrows(IllegalStateException.class, () -> config.getFaultStrategiesUrl(),
                "Should throw when getting fault strategies URL without API config");
        assertThrows(IllegalStateException.class, () -> config.getSimulationResultsUrl());
    }
}
