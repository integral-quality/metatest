package io.antigen.gradle;

import java.util.ArrayList;
import java.util.List;

public class AntigenExtension {

    private String spec;
    private List<String> requirements = new ArrayList<>();
    private int maxRetries = 5;
    private boolean verbose = false;

    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }

    public List<String> getRequirements() { return requirements; }
    public void setRequirements(List<String> requirements) { this.requirements = requirements; }
    public void requirement(String req) { this.requirements.add(req); }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public boolean isVerbose() { return verbose; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }
}
