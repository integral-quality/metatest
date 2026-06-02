package io.antigen.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Loads per-test-class .antigen.yml config files from the classpath.
 *
 * Convention:
 *   File: src/test/resources/antigen/<fully.qualified.ClassName>.antigen.yml
 *   Example: src/test/resources/antigen/com.example.orders.OrderApiTest.antigen.yml
 *
 * Files are discovered lazily when the test class is first intercepted.
 * Results (including "not found") are cached by TestScopedConfigCache.
 */
public class TestScopedConfigLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public Optional<TestScopedConfig> load(Class<?> testClass) {
        String[] candidates = {
            "antigen/" + testClass.getName() + ".antigen.yml"
        };

        for (String resourcePath : candidates) {
            InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                is = testClass.getClassLoader().getResourceAsStream(resourcePath);
            }
            if (is == null) continue;

            try (InputStream stream = is) {
                TestScopedConfig config = YAML_MAPPER.readValue(stream, TestScopedConfig.class);
                System.out.println("[Antigen] Loaded test-scoped config: " + resourcePath);
                return Optional.of(config);
            } catch (IOException e) {
                System.err.println("[Antigen] Failed to parse " + resourcePath + ": " + e.getMessage());
                return Optional.empty();
            }
        }

        return Optional.empty();
    }
}
