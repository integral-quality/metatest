package io.antigen.core.config;

import io.antigen.core.api.FaultStrategyApiClient;
import io.antigen.core.api.dto.ContractFaultStrategyListResponse;
import io.antigen.core.api.dto.ContractFaultStrategyResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration source that fetches fault strategies from the Antigen API server.
 * Requires API key authentication.
 */
public class ApiConfigurationSource implements ConfigurationSource {

    private final FaultStrategyApiClient apiClient;

    public ApiConfigurationSource() {
        this.apiClient = new FaultStrategyApiClient();
    }

    @Override
    public List<FaultCollection> getEnabledFaults() {
        List<FaultCollection> enabledFaults = new ArrayList<>();

        try {
            ContractFaultStrategyListResponse response = apiClient.getContractFaultStrategies();

            // Get the first enabled strategy (for now, assuming one strategy per project)
            ContractFaultStrategyResponse strategy = response.getStrategies().stream()
                    .filter(s -> s.getEnabled())
                    .findFirst()
                    .orElse(null);

            if (strategy == null) {
                System.out.println("No enabled contract fault strategies found");
                return enabledFaults;
            }

            System.out.println("Using fault strategy: " + strategy.getName());

            ContractFaultStrategyResponse.Faults faults = strategy.getFaults();
            if (faults == null) {
                return enabledFaults;
            }

            if (faults.getNullField() != null && faults.getNullField().getEnabled()) {
                enabledFaults.add(FaultCollection.null_field);
            }
            if (faults.getMissingField() != null && faults.getMissingField().getEnabled()) {
                enabledFaults.add(FaultCollection.missing_field);
            }
            if (faults.getEmptyList() != null && faults.getEmptyList().getEnabled()) {
                enabledFaults.add(FaultCollection.empty_list);
            }
            if (faults.getEmptyString() != null && faults.getEmptyString().getEnabled()) {
                enabledFaults.add(FaultCollection.empty_string);
            }
            if (faults.getInvalidDataType() != null && faults.getInvalidDataType().getEnabled()) {
                enabledFaults.add(FaultCollection.invalid_value);
            }
            if (faults.getInvalidValue() != null && faults.getInvalidValue().getEnabled()) {
                enabledFaults.add(FaultCollection.invalid_value);
            }
            if (faults.getHttpMethodChange() != null && faults.getHttpMethodChange().getEnabled()) {
                enabledFaults.add(FaultCollection.http_method_change);
            }

            return enabledFaults;

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch fault strategies from API", e);
        }
    }

    @Override
    public boolean isEndpointExcluded(String endpoint) {
        // TODO
        return false;
    }

    @Override
    public boolean isTestExcluded(String testName) {
        // TODO
        return false;
    }

    @Override
    public String getSourceName() {
        return "API";
    }

    @Override
    public SimulatorConfig getConfig() {
        // API mode doesn't use full SimulatorConfig yet - return minimal config
        // TODO: Fetch full config from API in future
        SimulatorConfig config = new SimulatorConfig();

        // Set default simulation settings for API mode
        SimulatorConfig.Simulation simulation = new SimulatorConfig.Simulation();
        simulation.only_success_responses = true;  // Default: only 2xx
        simulation.skip_collections_response = true;  // Default: skip arrays
        simulation.min_response_fields = 1;
        simulation.skip_if_contains_fields = List.of("error", "detail", "message", "errorMessage");
        config.simulation = simulation;

        return config;
    }
}
