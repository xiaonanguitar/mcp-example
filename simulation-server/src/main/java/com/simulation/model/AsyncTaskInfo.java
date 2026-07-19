package com.simulation.model;

import java.time.LocalDateTime;

public class AsyncTaskInfo {
    private String taskId;
    private String status;       // pending, running, completed, failed
    private int progress;        // 0-100
    private String tool;
    private String action;
    private String message;
    private Object result;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime completeTime;
    private String errorMessage;

    public AsyncTaskInfo() {}

    public AsyncTaskInfo(String taskId, String tool, String action) {
        this.taskId = taskId;
        this.tool = tool;
        this.action = action;
        this.status = "pending";
        this.progress = 0;
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
