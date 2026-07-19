# AI 仿真助手 - 架构设计文档

## 一、项目概述

本项目基于 **MCP (Model Context Protocol)** 协议，构建了一套 AI 辅助物理仿真平台。用户通过自然语言描述仿真需求，OpenCode 智能体自动调用合适的仿真工具执行任务。

### 技术栈

| 组件 | 技术 | 端口 |
|------|------|------|
| 仿真执行服务 | Java Spring Boot 3.2 | 8080 |
| MCP 协议服务 | Python FastMCP | 3000 |
| 智能体服务 | OpenCode | 4096 |
| 前端界面 | Vue 3 + Vite | 5173 |

---

## 二、整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                          用户浏览器                                  │
│                     http://localhost:5173                            │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                    Vue 3 前端 (ChatPanel)                     │  │
│  │         POST /session/:id/message  ←→  GET /event (SSE)      │  │
│  └──────────────────────────┬────────────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────────────┘
                              │ HTTP / SSE
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      OpenCode 智能体服务                             │
│                     http://localhost:4096                            │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  LLM 大模型 + MCP 工具调度引擎                                 │  │
│  │  - 理解用户自然语言意图                                        │  │
│  │  - 决定调用哪个 MCP 工具                                       │  │
│  │  - 解析工具返回结果并生成回答                                   │  │
│  └──────────────────────────┬────────────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────────────┘
                              │ MCP Protocol (Streamable HTTP)
                              │ POST http://localhost:3000/mcp
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Python MCP 服务 (FastMCP)                        │
│                   http://localhost:3000                              │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  MCP 工具层 - 协议转换与API桥接                                │  │
│  │                                                               │  │
│  │  simulation_execute(tool, action, params)                     │  │
│  │  simulation_get_status(task_id)                               │  │
│  │  simulation_list_tools(tool_name)                             │  │
│  └──────────────────────────┬────────────────────────────────────┘  │
│                             │ httpx (HTTP Client)                   │
└─────────────────────────────┼───────────────────────────────────────┘
                              │ HTTP REST API
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  Java Spring Boot 仿真执行服务                       │
│                   http://localhost:8080                              │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  SimulationController → SimulationService                     │  │
│  │                                                               │  │
│  │  GET  /api/simulation/tools         查询可用工具               │  │
│  │  POST /api/simulation/execute       执行仿真任务               │  │
│  │  GET  /api/simulation/task/{id}     查询任务状态               │  │
│  │                                                               │  │
│  │  ┌─────────────┐  ┌──────────────┐                            │  │
│  │  │  Lumerical  │  │  HyperWorks  │  ← Mock 仿真结果           │  │
│  │  │  FDTD/MODE  │  │  OptiStruct  │                            │  │
│  │  └─────────────┘  └──────────────┘                            │  │
│  └───────────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  McpServerLauncher (Spring EventListener)                     │  │
│  │  应用启动时自动拉起 Python MCP 进程                             │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 三、MCP 服务拉起原理

### 3.1 核心机制：Java 进程内嵌 Python 子进程

MCP 服务不是独立部署的，而是由 Java 服务在启动时自动拉起。核心原理如下：

```
Java Spring Boot 启动流程:
    │
    ▼
ApplicationReadyEvent 触发
    │
    ▼
McpServerLauncher.startMcpServer()
    │
    ├── 1. 定位 Python 可执行文件: py -3.12
    ├── 2. 定位 MCP 脚本: simulation-mcp/server.py
    ├── 3. 构建 ProcessBuilder 命令
    ├── 4. 启动子进程: pb.start()
    ├── 5. 启动守护线程读取子进程输出
    └── 6. 注册 JVM ShutdownHook 清理子进程
```

### 3.2 关键代码解析

#### 3.2.1 事件监听启动

```java
// McpServerLauncher.java
@Service
public class McpServerLauncher {

    @EventListener(ApplicationReadyEvent.class)  // Spring 容器完全就绪后触发
    public void startMcpServer() {
        // ... 启动逻辑
    }
}
```

**原理**：`ApplicationReadyEvent` 在 Spring Boot 所有 Bean 初始化完成、所有 `CommandLineRunner` 和 `ApplicationRunner` 执行完毕后触发。此时 Web 服务器（Tomcat）已启动，可以确保 MCP 服务启动后能正常连接到 Java API。

#### 3.2.2 ProcessBuilder 子进程管理

```java
ProcessBuilder pb = new ProcessBuilder(
    "py", "-3.12",                        // Python 3.12 解释器
    serverScript.toString(),              // server.py 脚本路径
    "--port", String.valueOf(mcpPort),    // MCP 服务端口
    "--java-url", "http://localhost:" + javaPort  // Java API 地址
);
pb.directory(mcpDir.toFile());           // 设置工作目录
pb.redirectErrorStream(true);            // 合并 stderr 到 stdout

mcpProcess = pb.start();                 // 启动子进程
```

**原理**：`ProcessBuilder` 是 Java 标准库提供的进程创建工具。调用 `start()` 后，JVM 会 fork 出一个独立的 OS 进程运行 Python 脚本。两个进程通过以下方式通信：

| 通道 | 方向 | 用途 |
|------|------|------|
| `mcpProcess.getInputStream()` | Python → Java | 读取 MCP 服务日志输出 |
| 命令行参数 | Java → Python | 传递端口、API 地址等配置 |
| HTTP (localhost:8080) | Python → Java | MCP 工具调用仿真 API |

#### 3.2.3 守护线程日志转发

```java
Thread outputThread = new Thread(() -> {
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(mcpProcess.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
            log.info("[MCP] {}", line);  // 转发到 Spring 日志
        }
    }
});
outputThread.setDaemon(true);  // 守护线程，JVM 退出时自动结束
outputThread.start();
```

**原理**：子进程的 stdout 是独立的输出流。Java 主线程不能阻塞等待子进程输出，因此创建一个守护线程持续读取。设置 `daemon=true` 确保该线程不会阻止 JVM 关闭。

#### 3.2.4 优雅关闭（ShutdownHook）

```java
Runtime.getRuntime().addShutdownHook(new Thread(this::stopMcpServer));

public void stopMcpServer() {
    if (mcpProcess != null && mcpProcess.isAlive()) {
        mcpProcess.destroy();              // 先发送 SIGTERM
        if (!mcpProcess.waitFor(5, SECONDS)) {
            mcpProcess.destroyForcibly();  // 5秒后强制 SIGKILL
        }
    }
}
```

**原理**：JVM 关闭时（Ctrl+C 或 kill），ShutdownHook 会自动执行。`destroy()` 发送优雅终止信号，给 Python 进程机会清理资源（关闭端口、保存状态）。超时后 `destroyForcibly()` 强制终止。

### 3.3 进程生命周期图

```
                    ┌──────────────────────────────────┐
                    │         JVM 启动                  │
                    └──────────────┬───────────────────┘
                                   │
                    ┌──────────────▼───────────────────┐
                    │   Spring Boot 初始化               │
                    │   - 加载 application.yml          │
                    │   - 创建 McpServerLauncher Bean   │
                    └──────────────┬───────────────────┘
                                   │
                    ┌──────────────▼───────────────────┐
                    │   Tomcat 启动 (port 8080)         │
                    │   - SimulationController 注册     │
                    └──────────────┬───────────────────┘
                                   │
                    ┌──────────────▼───────────────────┐
                    │   ApplicationReadyEvent 触发      │
                    └──────────────┬───────────────────┘
                                   │
                    ┌──────────────▼───────────────────┐
                    │   startMcpServer() 执行           │
                    │   - ProcessBuilder 构建           │
                    │   - pb.start() 创建子进程         │
                    └──────────────┬───────────────────┘
                                   │
                    ┌──────────────▼───────────────────┐
                    │   Python 子进程启动                │
                    │   - FastMCP 初始化                 │
                    │   - Uvicorn 监听 port 3000        │
                    │   - 注册 MCP 工具                 │
                    └──────────────┬───────────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
    ┌─────────▼─────────┐ ┌───────▼───────┐ ┌─────────▼─────────┐
    │   Java 8080       │ │  Python 3000  │ │  OpenCode 4096    │
    │   仿真 API 服务   │ │  MCP 协议服务  │ │  智能体服务        │
    └─────────▲─────────┘ └───────▲───────┘ └─────────▲─────────┘
              │                    │                    │
              │    HTTP REST       │   MCP Protocol     │   HTTP/SSE
              │                    │                    │
              └────────────────────┴────────────────────┘
                                   │
                    ┌──────────────▼───────────────────┐
                    │          JVM 关闭                 │
                    │   ShutdownHook 触发               │
                    │   - stopMcpServer() 执行          │
                    │   - Python 进程被终止              │
                    └──────────────────────────────────┘
```

---

## 四、MCP 协议通信流程

### 4.1 工具发现

```
OpenCode                     MCP Server (port 3000)
    │                              │
    │  POST /mcp                   │
    │  {method: "tools/list"}      │
    │  ──────────────────────────> │
    │                              │
    │  {result: {tools: [...]}}    │
    │  <────────────────────────── │
    │                              │
    │  发现 3 个工具:               │
    │  - simulation_execute        │
    │  - simulation_get_status     │
    │  - simulation_list_tools     │
```

### 4.2 工具调用

```
用户: "帮我用 Lumerical FDTD 仿真一个波导"
    │
    ▼
OpenCode (LLM 理解意图)
    │
    │  POST /mcp
    │  {
    │    method: "tools/call",
    │    params: {
    │      name: "simulation_execute",
    │      arguments: {
    │        tool: "lumerical",
    │        action: "FDTD",
    │        parameters: "{\"type\": \"waveguide\"}"
    │      }
    │    }
    │  }
    ▼
MCP Server (Python)
    │
    │  POST http://localhost:8080/api/simulation/execute
    │  {
    │    "tool": "lumerical",
    │    "action": "FDTD",
    │    "parameters": "{\"type\": \"waveguide\"}"
    │  }
    ▼
Java Simulation Service
    │
    │  返回 Mock 仿真结果:
    │  {
    │    "status": "success",
    │    "result": {
    │      "field_intensity_max": 0.9234,
    │      "transmission_coefficient": 0.9512,
    │      "mesh_cells": 312456
    │    }
    │  }
    ▼
MCP Server → OpenCode → 用户
    │
    │  "仿真完成。Lumerical FDTD 执行结果:
    │   最大场强 0.9234, 透射系数 0.9512,
    │   网格单元数 312456, 仿真收敛。"
```

---

## 五、数据流完整链路

```
┌─────────────────────────────────────────────────────────────────┐
│                        数据流向图                                │
└─────────────────────────────────────────────────────────────────┘

[用户输入]
    │
    │ "用 Lumerical FDTD 仿真 1550nm 波导"
    ▼
[Vue 3 前端]
    │ POST /session (创建会话)
    │ POST /session/:id/message (发送消息)
    ▼
[OpenCode 智能体]
    │ LLM 推理 → 决定调用 simulation_execute
    │ POST /mcp (MCP JSON-RPC 2.0)
    ▼
[Python MCP Server]
    │ 解析 MCP 工具调用
    │ httpx → POST /api/simulation/execute
    ▼
[Java Spring Boot]
    │ SimulationService.executeSimulation()
    │ 生成 Mock 仿真结果
    │ 返回 JSON 响应
    ▼
[Python MCP Server]
    │ 封装为 MCP content 格式
    │ 返回给 OpenCode
    ▼
[OpenCode 智能体]
    │ LLM 解析结果 → 生成自然语言回答
    │ GET /event (SSE 流式推送)
    ▼
[Vue 3 前端]
    │ 监听 message.part.updated 事件
    │ 流式渲染 AI 回答
    ▼
[用户看到结果]
```

---

## 六、配置说明

### 6.1 Java 配置 (application.yml)

```yaml
server:
  port: 8080

simulation:
  tools:
    - name: lumerical
      description: "Lumerical光学仿真工具"
      capabilities: ["FDTD", "MODE", "DEVICE", "INTERCONNECT"]
    - name: hyperworks
      description: "HyperWorks多物理场仿真工具"
      capabilities: ["OptiStruct", "Radioss", "AcuSolve", "Flux"]
  mcp:
    enabled: true     # 是否自动拉起 MCP 服务
    port: 3000        # MCP 服务端口
```

### 6.2 OpenCode 配置 (opencode.json)

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "simulation": {
      "type": "remote",
      "url": "http://localhost:3000/mcp",
      "enabled": true
    }
  }
}
```

### 6.3 前端代理 (vite.config.js)

```javascript
proxy: {
  '/session': { target: 'http://localhost:4096' },  // OpenCode
  '/event':   { target: 'http://localhost:4096' }   // SSE 流
}
```

---

## 七、启动顺序

```bash
# 1. 启动 Java 服务 (自动拉起 MCP)
cd simulation-server && mvn spring-boot:run
# 控制台输出: [MCP] Starting Simulation MCP Server on port 3000...

# 2. 启动 OpenCode 智能体
cd E:\Code\MCP_Example && opencode serve --port 4096

# 3. 启动前端
cd simulation-ui && npm run dev
# 访问 http://localhost:5173
```
