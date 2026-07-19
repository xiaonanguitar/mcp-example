package com.simulation.controller;

import com.simulation.model.AsyncTaskInfo;
import com.simulation.model.SimulationRequest;
import com.simulation.model.SimulationResponse;
import com.simulation.model.SimulationTool;
import com.simulation.service.SimulationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
@CrossOrigin(origins = "*")
public class SimulationController {

    private static final Logger log = LoggerFactory.getLogger(SimulationController.class);
    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/tools")
    public ResponseEntity<List<SimulationTool>> listTools() {
        log.info("[API] GET /api/simulation/tools");
        List<SimulationTool> tools = simulationService.listTools();
        log.info("[API] 返回 {} 个仿真工具: {}", tools.size(),
            tools.stream().map(SimulationTool::getName).toList());
        return ResponseEntity.ok(tools);
    }

    @PostMapping("/execute")
    public ResponseEntity<SimulationResponse> execute(@RequestBody SimulationRequest request) {
        log.info("[API] POST /api/simulation/execute - tool={}, action={}, params={}",
            request.getTool(), request.getAction(), request.getParameters());
        SimulationResponse response = simulationService.executeSimulation(request);
        log.info("[API] 执行结果 - status={}, taskId={}, message={}",
            response.getStatus(),
            response.getMetadata() != null ? response.getMetadata().get("taskId") : "N/A",
            response.getMessage());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/execute/async")
    public ResponseEntity<Map<String, String>> executeAsync(@RequestBody SimulationRequest request) {
        log.info("[API] POST /api/simulation/execute/async - tool={}, action={}, params={}",
            request.getTool(), request.getAction(), request.getParameters());
        String taskId = simulationService.executeSimulationAsync(request);
        log.info("[API] 异步任务已提交 - taskId={}", taskId);
        return ResponseEntity.ok(Map.of(
            "taskId", taskId,
            "status", "pending",
            "message", "异步仿真任务已提交，请使用 taskId 查询进度"
        ));
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<SimulationResponse> getTaskStatus(@PathVariable String taskId) {
        log.info("[API] GET /api/simulation/task/{}", taskId);
        SimulationResponse response = simulationService.getTaskStatus(taskId);
        log.info("[API] 任务状态 - status={}, message={}", response.getStatus(), response.getMessage());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/async/task/{taskId}")
    public ResponseEntity<Map<String, Object>> getAsyncTaskStatus(@PathVariable String taskId) {
        log.info("[API] GET /api/simulation/async/task/{}", taskId);
        AsyncTaskInfo taskInfo = simulationService.getAsyncTaskStatus(taskId);
        if (taskInfo == null) {
            log.warn("[API] 异步任务不存在: {}", taskId);
            return ResponseEntity.ok(Map.of(
                "status", "not_found",
                "message", "任务不存在: " + taskId
            ));
        }
        log.info("[API] 异步任务状态 - taskId={}, status={}, progress={}%", taskId, taskInfo.getStatus(), taskInfo.getProgress());
        return ResponseEntity.ok(Map.of(
            "taskId", taskInfo.getTaskId(),
            "status", taskInfo.getStatus(),
            "progress", taskInfo.getProgress(),
            "tool", taskInfo.getTool() != null ? taskInfo.getTool() : "",
            "action", taskInfo.getAction() != null ? taskInfo.getAction() : "",
            "message", taskInfo.getMessage() != null ? taskInfo.getMessage() : "",
            "result", taskInfo.getResult() != null ? taskInfo.getResult() : "",
            "errorMessage", taskInfo.getErrorMessage() != null ? taskInfo.getErrorMessage() : ""
        ));
    }

    @GetMapping("/async/tasks")
    public ResponseEntity<List<Map<String, Object>>> listAsyncTasks() {
        log.info("[API] GET /api/simulation/async/tasks");
        List<Map<String, Object>> tasks = simulationService.listAsyncTasks();
        log.info("[API] 返回 {} 个异步任务", tasks.size());
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("[API] GET /api/simulation/health");
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "simulation-server",
            "tools", simulationService.listTools().stream()
                .map(SimulationTool::getName)
                .toList()
        ));
    }
}
