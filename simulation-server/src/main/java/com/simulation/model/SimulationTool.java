package com.simulation.model;

import java.util.List;
import java.util.Map;

public class SimulationTool {
    private String name;
    private String description;
    private List<String> capabilities;
    private Map<String, Object> parameters;

    public SimulationTool() {}

    public SimulationTool(String name, String description, List<String> capabilities) {
        this.name = name;
        this.description = description;
        this.capabilities = capabilities;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
