package com.simulation.service;

import com.simulation.model.AsyncTaskInfo;
import com.simulation.model.SimulationRequest;
import com.simulation.model.SimulationResponse;
import com.simulation.model.SimulationTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);
    private final Map<String, SimulationTool> registeredTools = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> taskHistory = new ConcurrentHashMap<>();
    private final Map<String, AsyncTaskInfo> asyncTasks = new ConcurrentHashMap<>();

    public SimulationService() {
        initMockTools();
    }

    private void initMockTools() {
        SimulationTool lumerical = new SimulationTool(
            "lumerical",
            "Lumerical光学仿真工具 - 用于光子学和光学器件仿真",
            Arrays.asList("FDTD", "MODE", "DEVICE", "INTERCONNECT")
        );
        SimulationTool hyperworks = new SimulationTool(
            "hyperworks",
            "HyperWorks多物理场仿真工具 - 用于结构、流体、热分析",
            Arrays.asList("OptiStruct", "Radioss", "AcuSolve", "Flux")
        );
        registeredTools.put("lumerical", lumerical);
        registeredTools.put("hyperworks", hyperworks);
        log.info("仿真工具初始化完成: {}", registeredTools.keySet());
    }

    public List<SimulationTool> listTools() {
        return new ArrayList<>(registeredTools.values());
    }

    // ==================== 同步仿真 ====================

    public SimulationResponse executeSimulation(SimulationRequest request) {
        String toolName = request.getTool();
        String action = request.getAction();
        log.info("[仿真执行] 开始 - tool={}, action={}, params={}", toolName, action, request.getParameters());

        if (!registeredTools.containsKey(toolName)) {
            log.warn("[仿真执行] 未知工具: {}", toolName);
            return SimulationResponse.error(toolName, action,
                "未知的仿真工具: " + toolName + "。可用工具: " + registeredTools.keySet());
        }

        SimulationTool tool = registeredTools.get(toolName);
        if (!tool.getCapabilities().contains(action)) {
            log.warn("[仿真执行] 工具 {} 不支持操作: {}", toolName, action);
            return SimulationResponse.error(toolName, action,
                "工具 " + toolName + " 不支持操作: " + action + "。支持的操作: " + tool.getCapabilities());
        }

        String taskId = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> mockResult = generateMockResult(toolName, action, request.getParameters());
        taskHistory.put(taskId, mockResult);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("taskId", taskId);
        metadata.put("executionTimeMs", 150 + new Random().nextInt(850));
        metadata.put("nodeId", "compute-node-" + (1 + new Random().nextInt(3)));

        SimulationResponse response = SimulationResponse.success(
            toolName, action, mockResult,
            toolName + " " + action + " 仿真任务执行成功"
        );
        response.setMetadata(metadata);
        log.info("[仿真执行] 完成 - taskId={}, 耗时={}ms", taskId, metadata.get("executionTimeMs"));
        return response;
    }

    // ==================== 异步仿真 ====================

    public String executeSimulationAsync(SimulationRequest request) {
        String toolName = request.getTool();
        String action = request.getAction();
        String taskId = UUID.randomUUID().toString().substring(0, 8);

        log.info("[异步仿真] 提交任务 - taskId={}, tool={}, action={}", taskId, toolName, action);

        AsyncTaskInfo taskInfo = new AsyncTaskInfo(taskId, toolName, action);
        taskInfo.setMessage("任务已提交，等待执行...");
        asyncTasks.put(taskId, taskInfo);

        runAsyncSimulation(taskId, toolName, action, request.getParameters());

        return taskId;
    }

    @Async
    public void runAsyncSimulation(String taskId, String toolName, String action, String params) {
        AsyncTaskInfo taskInfo = asyncTasks.get(taskId);
        try {
            taskInfo.setStatus("running");
            taskInfo.setProgress(10);
            taskInfo.setMessage("正在初始化仿真环境...");
            log.info("[异步仿真] taskId={} - 初始化仿真环境", taskId);
            Thread.sleep(2000);

            taskInfo.setProgress(30);
            taskInfo.setMessage("正在构建网格模型...");
            log.info("[异步仿真] taskId={} - 构建网格模型", taskId);
            Thread.sleep(3000);

            taskInfo.setProgress(50);
            taskInfo.setMessage("正在设置边界条件...");
            log.info("[异步仿真] taskId={} - 设置边界条件", taskId);
            Thread.sleep(2000);

            taskInfo.setProgress(70);
            taskInfo.setMessage("正在执行求解计算...");
            log.info("[异步仿真] taskId={} - 执行求解计算", taskId);
            Thread.sleep(4000);

            taskInfo.setProgress(90);
            taskInfo.setMessage("正在后处理结果...");
            log.info("[异步仿真] taskId={} - 后处理结果", taskId);
            Thread.sleep(2000);

            Map<String, Object> mockResult = generateMockResult(toolName, action, params);
            taskInfo.setResult(mockResult);
            taskInfo.setProgress(100);
            taskInfo.setStatus("completed");
            taskInfo.setCompleteTime(LocalDateTime.now());
            taskInfo.setMessage("仿真任务执行完成");
            log.info("[异步仿真] taskId={} - 任务完成", taskId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            taskInfo.setStatus("failed");
            taskInfo.setErrorMessage("任务被中断: " + e.getMessage());
            taskInfo.setMessage("仿真任务执行失败");
            log.error("[异步仿真] taskId={} - 任务被中断", taskId, e);
        } catch (Exception e) {
            taskInfo.setStatus("failed");
            taskInfo.setErrorMessage(e.getMessage());
            taskInfo.setMessage("仿真任务执行失败: " + e.getMessage());
            log.error("[异步仿真] taskId={} - 任务异常", taskId, e);
        }
    }

    // ==================== 任务查询 ====================

    public SimulationResponse getTaskStatus(String taskId) {
        log.info("[任务查询] taskId={}", taskId);
        if (!taskHistory.containsKey(taskId)) {
            log.warn("[任务查询] 任务不存在: {}", taskId);
            return SimulationResponse.error("system", "status", "任务不存在: " + taskId);
        }
        Map<String, Object> result = taskHistory.get(taskId);
        SimulationResponse response = SimulationResponse.success(
            "system", "status", result, "任务状态查询成功"
        );
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("taskId", taskId);
        response.setMetadata(metadata);
        log.info("[任务查询] 查询成功 - taskId={}", taskId);
        return response;
    }

    public AsyncTaskInfo getAsyncTaskStatus(String taskId) {
        log.info("[异步任务查询] taskId={}", taskId);
        return asyncTasks.get(taskId);
    }

    public List<Map<String, Object>> listAsyncTasks() {
        log.info("[异步任务列表] 查询所有异步任务");
        List<Map<String, Object>> taskList = new ArrayList<>();
        for (Map.Entry<String, AsyncTaskInfo> entry : asyncTasks.entrySet()) {
            AsyncTaskInfo info = entry.getValue();
            Map<String, Object> taskMap = new HashMap<>();
            taskMap.put("taskId", info.getTaskId());
            taskMap.put("status", info.getStatus());
            taskMap.put("progress", info.getProgress());
            taskMap.put("tool", info.getTool() != null ? info.getTool() : "");
            taskMap.put("action", info.getAction() != null ? info.getAction() : "");
            taskMap.put("message", info.getMessage() != null ? info.getMessage() : "");
            taskList.add(taskMap);
        }
        log.info("[异步任务列表] 共 {} 个任务", taskList.size());
        return taskList;
    }

    // ==================== Mock 数据生成 ====================

    private Map<String, Object> generateMockResult(String tool, String action, String params) {
        Map<String, Object> result = new HashMap<>();
        Random rand = new Random();

        switch (tool) {
            case "lumerical":
                result.put("type", "optical_simulation");
                result.put("wavelength_nm", 1550.0);
                result.put("field_intensity_max", 0.87 + rand.nextDouble() * 0.12);
                result.put("convergence", true);
                result.put("mesh_cells", 250000 + rand.nextInt(100000));
                result.put("transmission_coefficient", 0.92 + rand.nextDouble() * 0.06);
                result.put("reflection_coefficient", 0.03 + rand.nextDouble() * 0.05);
                result.put("executionTime", (rand.nextDouble() * 2 + 0.5) + "s");
                break;

            case "hyperworks":
                result.put("type", "structural_simulation");
                result.put("max_stress_mpa", 245.6 + rand.nextDouble() * 50);
                result.put("max_displacement_mm", 0.12 + rand.nextDouble() * 0.08);
                result.put("safety_factor", 1.8 + rand.nextDouble() * 1.2);
                result.put("convergence", true);
                result.put("iterations", 42 + rand.nextInt(20));
                result.put("nodes", 125000 + rand.nextInt(75000));
                result.put("elements", 280000 + rand.nextInt(120000));
                result.put("executionTime", (rand.nextDouble() * 5 + 1) + "s");
                break;
        }

        result.put("tool", tool);
        result.put("action", action);
        result.put("parameters", params);
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("status", "completed");
        return result;
    }
}
