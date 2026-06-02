package io.antigen.ai.phases;

import io.antigen.ai.model.EscapedFault;
import lombok.Value;

import java.util.List;

@Value
public class AntigenPhase implements PhaseResult {
    boolean success;
    List<EscapedFault> escapedFaults;
    double faultDetectionRate;
    int totalFaults;
    int caughtFaults;

    public static AntigenPhase success(double detectionRate, int total, int caught) {
        return new AntigenPhase(true, List.of(), detectionRate, total, caught);
    }

    public static AntigenPhase failed(List<EscapedFault> escaped, double detectionRate, int total, int caught) {
        return new AntigenPhase(false, escaped, detectionRate, total, caught);
    }

    public boolean hasEscapedFaults() {
        return !escapedFaults.isEmpty();
    }

    @Override
    public String getFeedback() {
        if (success) {
            return String.format("Antigen passed - %.1f%% fault detection rate (%d/%d faults caught)",
                faultDetectionRate * 100, caughtFaults, totalFaults);
        }

        return String.format("""
            ANTIGEN FAILURE - Your tests did NOT catch %d out of %d injected faults (%.1f%% detection rate).

            IMPORTANT: Use the Read tool to read fault_simulation_report.json in the project root.
            DO NOT write scripts to parse it - read it directly with the Read tool.

            The report structure:
            {
              "/api/endpoint": {
                "contractFaultCount": 5,
                "invariantFaultCount": 2,
                "contractFaultsCaught": 3,
                "invariantFaultsCaught": 1,
                "contract_faults": {
                  "fault_type": {
                    "field_name": {
                      "caught_by_any_test": false,  <- KEY FIELD: false means this fault escaped
                      "tested_by": ["testMethod"],
                      "caught_by": []
                    }
                  }
                },
                "invariant_faults": {
                  "invariant_name": {
                    "caught_by_any_test": false,  <- KEY FIELD: false means this fault escaped
                    "tested_by": ["testMethod"],
                    "caught_by": []
                  }
                }
              }
            }

            Your task:
            1. Read fault_simulation_report.json from the project root
            2. Find all entries where "caught_by_any_test": false (in both contract_faults and invariant_faults)
            3. Look at the "tested_by" array to see which tests ran against this fault
            4. Update those tests to add proper assertions to catch that particular fault

            Focus on the faults where "caught_by_any_test": false - these are the ones that escaped detection.
            "caught_by_any_test" is set to true when at least one test catches that fault
            """,
            escapedFaults.size(),
            totalFaults,
            faultDetectionRate * 100);
    }
}
