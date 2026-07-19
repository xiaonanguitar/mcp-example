package com.simulation.model;

import java.time.LocalDateTime;

public class SimulationRequest {
    private String tool;
    private String action;
    private String parameters;
    private String sessionId;

    public SimulationRequest() {}

    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
