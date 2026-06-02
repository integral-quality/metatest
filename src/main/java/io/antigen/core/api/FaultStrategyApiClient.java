package io.antigen.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.antigen.core.api.dto.ContractFaultStrategyListResponse;
import io.antigen.core.api.dto.SubmitSimulationResultsRequest;
import io.antigen.core.config.AntigenConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class FaultStrategyApiClient {
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AntigenConfig config;


    public FaultStrategyApiClient() {
        this.config = AntigenConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        System.out.println("Initialized Antigen API client with config: " + config);
    }

    private HttpRequest.Builder createAuthenticatedRequestBuilder() {
        return HttpRequest.newBuilder()
                .header("X-API-Key", config.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30));
    }
    
    public ContractFaultStrategyListResponse getContractFaultStrategies() {
        try {
            HttpRequest request = createAuthenticatedRequestBuilder()
                    .uri(URI.create(config.getFaultStrategiesUrl()))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Contract Fault Strategy Response: " + response.body());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch fault strategies. HTTP " + response.statusCode() + ": " + response.body());
            }
            
            return objectMapper.readValue(response.body(), ContractFaultStrategyListResponse.class);
            
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error communicating with fault strategy API", e);
        }
    }
    
    public void submitSimulationResults(SubmitSimulationResultsRequest request) {
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            
            HttpRequest httpRequest = createAuthenticatedRequestBuilder()
                    .uri(URI.create(config.getSimulationResultsUrl()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("Simulation Results Response: " + response.body());
            
            if (response.statusCode() != 201) {
                throw new RuntimeException("Failed to submit simulation results. HTTP " + response.statusCode() + ": " + response.body());
            }
            
            System.out.println("Successfully submitted simulation results to API using API key authentication");
            
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error submitting simulation results to API", e);
        }
    }
}