package com.simulation.model;

import java.time.LocalDateTime;
import java.util.Map;

public class SimulationResponse {
    private String status;
    private String tool;
    private String action;
    private Object result;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;

    public SimulationResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public static SimulationResponse success(String tool, String action, Object result, String message) {
        SimulationResponse resp = new SimulationResponse();
        resp.setStatus("success");
        resp.setTool(tool);
        resp.setAction(action);
        resp.setResult(result);
        resp.setMessage(message);
        return resp;
    }

    public static SimulationResponse error(String tool, String action, String message) {
        SimulationResponse resp = new SimulationResponse();
        resp.setStatus("error");
        resp.setTool(tool);
        resp.setAction(action);
        resp.setMessage(message);
        return resp;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
