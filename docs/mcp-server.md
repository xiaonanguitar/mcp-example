# Simulation MCP Server - 实现文档

## 一、概述

本服务是一个基于 **FastMCP** 框架实现的 MCP (Model Context Protocol) 服务器，负责将 Java 仿真执行 API 桥接为 MCP 协议工具，供 OpenCode 智能体调用。

### 核心定位

```
OpenCode (LLM)  <--->  MCP Server (本服务)  <--->  Java 仿真 API
       |                    |                        |
   智能体决策          协议转换/桥接             仿真任务执行
```

### 技术依赖

| 包名 | 版本 | 用途 |
|------|------|------|
| `mcp[cli]` | >=1.0.0 | FastMCP 框架，MCP 协议实现 |
| `httpx` | >=0.27.0 | 异步 HTTP 客户端，调用 Java API |
| `pydantic` | >=2.0.0 | 输入参数校验与序列化 |

---

## 二、架构设计

### 2.1 分层架构

```
+-----------------------------------------------------------+
|                   MCP Protocol Layer                       |
|            Streamable HTTP (FastMCP + Uvicorn)             |
|                                                           |
|   POST /mcp  <-->  JSON-RPC 2.0  <-->  工具注册/调度      |
+------------------------------+----------------------------+
                               |
+------------------------------v----------------------------+
|                   Tool Layer (工具层)                       |
|                                                           |
|   simulation_execute      执行仿真                         |
|   simulation_get_status   查询状态                         |
|   simulation_list_tools   列出工具                         |
+------------------------------+----------------------------+
                               |
+------------------------------v----------------------------+
|                  Input Validation Layer                    |
|                  Pydantic Models                           |
|                                                           |
|   ExecuteSimulationInput    GetTaskStatusInput             |
|   ListToolsInput                                          |
+------------------------------+----------------------------+
                               |
+------------------------------v----------------------------+
|                   API Client Layer                         |
|              httpx AsyncClient                             |
|                                                           |
|   _call_java_api()    ->    Java REST API                 |
|   _format_error()     ->    统一错误格式                   |
+------------------------------+----------------------------+
                               |
                               v
                  Java Spring Boot (port 8080)
```

### 2.2 文件结构

```
simulation-mcp/
+-- server.py            # MCP 服务主程序 (167行)
+-- requirements.txt     # Python 依赖
+-- .venv/               # Python 虚拟环境
```

### 2.3 启动流程

```
__main__ 入口
    |
    +-- 1. argparse 解析命令行参数
    |      --port (默认 3000)
    |      --java-url (默认 http://localhost:8080)
    |
    +-- 2. 初始化 FastMCP 实例
    |      FastMCP("simulation_mcp", host="0.0.0.0", port=3000)
    |
    +-- 3. 注册 MCP 工具 (@mcp.tool 装饰器)
    |      simulation_execute
    |      simulation_get_status
    |      simulation_list_tools
    |
    +-- 4. 启动 HTTP 服务
           mcp.run(transport="streamable-http")
           -> Uvicorn 监听 0.0.0.0:3000
           -> 暴露 /mcp 端点
```

---

## 三、代码逐行解析

### 3.1 初始化与配置 (L1-L25)

**Shebang 与文档字符串 (L1-L8)**

```python
#!/usr/bin/env python3
"""
Simulation MCP Server

This MCP server bridges the MCP protocol to the Java Simulation API,
providing simulation tools for OpenCode to interact with.
Transport: Streamable HTTP (SSE mode).
"""
```

说明：shebang 行指定使用 python3 执行。文档字符串说明了服务的职责 - 桥接 MCP 协议和 Java 仿真 API。

**命令行参数解析 (L10-L20)**

```python
parser = argparse.ArgumentParser(description="Simulation MCP Server")
parser.add_argument("--port", type=int, default=3000, help="MCP server port")
parser.add_argument("--java-url", type=str, default="http://localhost:8080", help="Java backend URL")
args, _ = parser.parse_known_args()
```

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--port` | 3000 | MCP 服务监听端口 |
| `--java-url` | http://localhost:8080 | Java 后端 API 地址 |

使用 `parse_known_args()` 而非 `parse_args()`，允许忽略未知参数（由 FastMCP 内部处理）。

**FastMCP 实例初始化 (L22-L25)**

```python
JAVA_API_BASE = 'http://localhost:8080'
MCP_PORT = args.port

mcp = FastMCP("simulation_mcp", host="0.0.0.0", port=MCP_PORT)
```

| 参数 | 说明 |
|------|------|
| `"simulation_mcp"` | MCP 服务名称，客户端通过此名称识别服务 |
| `host="0.0.0.0"` | 监听所有网络接口（允许外部访问） |
| `port=MCP_PORT` | 监听端口 |

---

### 3.2 Pydantic 输入模型 (L28-L57)

**ExecuteSimulationInput (L28-L43)**

```python
class ExecuteSimulationInput(BaseModel):
    """Execute a simulation task using the specified tool and action."""
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)

    tool: str = Field(
        ...,
        description="Simulation tool name. Options: 'lumerical' (optical), 'hyperworks' (structural/multiphysics)"
    )
    action: str = Field(
        ...,
        description="Action to execute. Lumerical: FDTD/MODE/DEVICE/INTERCONNECT. HyperWorks: OptiStruct/Radioss/AcuSolve/Flux"
    )
    parameters: Optional[str] = Field(
        default=None,
        description="Optional JSON string of simulation parameters"
    )
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `tool` | str | 是 | 仿真工具名称 |
| `action` | str | 是 | 仿真操作类型 |
| `parameters` | str | 否 | JSON 格式的仿真参数 |

配置说明：
- `str_strip_whitespace=True`: 自动去除字符串首尾空白
- `validate_assignment=True`: 赋值时自动校验
- `Field(...)`: 必填字段标记

**GetTaskStatusInput (L46-L50)**

```python
class GetTaskStatusInput(BaseModel):
    """Query the status and results of a simulation task."""
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)

    task_id: str = Field(..., description="The task ID from a previous simulation execution")
```

单一必填字段 `task_id`，用于查询已提交的仿真任务状态。

**ListToolsInput (L53-L57)**

```python
class ListToolsInput(BaseModel):
    """List available simulation tools and their capabilities."""
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)

    tool_name: Optional[str] = Field(default=None, description="Optional filter by tool name")
```

可选字段 `tool_name`，用于按名称过滤工具列表。

---

### 3.3 API 客户端层 (L60-L79)

**异步 HTTP 请求封装 (L60-L64)**

```python
async def _call_java_api(endpoint: str, method: str = "GET", **kwargs) -> dict:
    async with httpx.AsyncClient(timeout=30.0, trust_env=False) as client:
        response = await client.request(method, f"{JAVA_API_BASE}{endpoint}", **kwargs)
        response.raise_for_status()
        return response.json()
```

| 参数 | 说明 |
|------|------|
| `endpoint` | API 路径，如 `/api/simulation/execute` |
| `method` | HTTP 方法，默认 GET |
| `**kwargs` | 传递给 httpx 的额外参数（如 `json=`, `data=`） |

关键设计：
- `async with`: 异步上下文管理器，请求完成后自动关闭连接
- `timeout=30.0`: 30秒超时，防止长时间阻塞
- `trust_env=False`: 不读取系统代理配置，直接连接
- `raise_for_status()`: HTTP 状态码非 2xx 时抛出异常

**统一错误处理 (L67-L79)**

```python
def _format_error(e: Exception) -> str:
    if isinstance(e, httpx.HTTPStatusError):
        status = e.response.status_code
        if status == 404:
            return "Error: API endpoint not found. Please check the Java server is running."
        elif status == 500:
            return "Error: Java server internal error."
        return f"Error: API request failed with status {status}"
    elif isinstance(e, httpx.ConnectError):
        return "Error: Cannot connect to Java simulation server."
    elif isinstance(e, httpx.TimeoutException):
        return "Error: Request timed out."
    return f"Error: {type(e).__name__}: {str(e)}"
```

错误类型映射：

| 异常类型 | HTTP 状态码 | 返回信息 |
|----------|------------|----------|
| `HTTPStatusError` | 404 | API 端点不存在 |
| `HTTPStatusError` | 500 | Java 服务内部错误 |
| `HTTPStatusError` | 其他 | 具体状态码 |
| `ConnectError` | - | 无法连接 Java 服务 |
| `TimeoutException` | - | 请求超时 |
| 其他异常 | - | 异常类型和消息 |

---

### 3.4 MCP 工具实现 (L82-L161)

#### 3.4.1 simulation_execute (L82-L106)

**工具注册装饰器**

```python
@mcp.tool(
    name="simulation_execute",
    annotations={
        "title": "Execute Simulation",
        "readOnlyHint": False,       # 会修改状态（创建任务）
        "destructiveHint": False,     # 不会删除数据
        "idempotentHint": False,      # 多次调用结果不同
        "openWorldHint": True         # 与外部系统交互
    }
)
```

注解说明：

| 注解 | 值 | 含义 |
|------|-----|------|
| `readOnlyHint` | False | 执行仿真会创建新任务 |
| `destructiveHint` | False | 不会破坏现有数据 |
| `idempotentHint` | False | 每次调用生成不同 taskId |
| `openWorldHint` | True | 需要调用外部 Java API |

**工具函数实现**

```python
async def simulation_execute(params: ExecuteSimulationInput) -> str:
    """Execute a physics simulation task.

    Supported tools and actions:
    Lumerical: FDTD, MODE, DEVICE, INTERCONNECT
    HyperWorks: OptiStruct, Radioss, AcuSolve, Flux
    """
    try:
        request_data = {"tool": params.tool, "action": params.action}
        if params.parameters:
            request_data["parameters"] = params.parameters
        result = await _call_java_api("/api/simulation/execute", method="POST", json=request_data)
        return json.dumps(result, indent=2, ensure_ascii=False)
    except Exception as e:
        return _format_error(e)
```

执行流程：

```
OpenCode 调用 simulation_execute
    |
    v
Pydantic 校验输入参数 (tool, action, parameters)
    |
    v
构建 request_data 字典
    |
    v
_call_java_api("POST", "/api/simulation/execute", json=request_data)
    |
    v
Java API 返回 JSON 结果
    |
    v
json.dumps() 格式化为可读字符串返回给 OpenCode
```

---

#### 3.4.2 simulation_get_status (L109-L125)

```python
@mcp.tool(
    name="simulation_get_status",
    annotations={
        "title": "Get Task Status",
        "readOnlyHint": True,        # 只读操作
        "destructiveHint": False,
        "idempotentHint": True,      # 多次查询结果一致
        "openWorldHint": True
    }
)
async def simulation_get_status(params: GetTaskStatusInput) -> str:
    """Query the status and results of a previously submitted simulation task."""
    try:
        result = await _call_java_api(f"/api/simulation/task/{params.task_id}")
        return json.dumps(result, indent=2, ensure_ascii=False)
    except Exception as e:
        return _format_error(e)
```

执行流程：

```
OpenCode 调用 simulation_get_status(task_id="a1b2c3d4")
    |
    v
GET /api/simulation/task/a1b2c3d4
    |
    v
返回任务状态和结果
```

---

#### 3.4.3 simulation_list_tools (L128-L161)

```python
@mcp.tool(
    name="simulation_list_tools",
    annotations={
        "title": "List Simulation Tools",
        "readOnlyHint": True,
        "destructiveHint": False,
        "idempotentHint": True,
        "openWorldHint": False        # 本地数据，不涉及外部调用
    }
)
async def simulation_list_tools(params: ListToolsInput) -> str:
    """List available simulation tools and their capabilities."""
    try:
        result = await _call_java_api("/api/simulation/tools")
        tools = result if isinstance(result, list) else result.get("tools", [])

        if params.tool_name:
            tools = [t for t in tools if t.get("name") == params.tool_name]
            if not tools:
                return f"No tool found with name '{params.tool_name}'."

        lines = ["# Available Simulation Tools", ""]
        for tool in tools:
            name = tool.get("name", "unknown")
            desc = tool.get("description", "No description")
            caps = tool.get("capabilities", [])
            lines.append(f"## {name}")
            lines.append(f"- **Description**: {desc}")
            lines.append(f"- **Capabilities**: {', '.join(caps)}")
            lines.append("")

        return "\n".join(lines) if tools else "No simulation tools registered."
    except Exception as e:
        return _format_error(e)
```

执行流程：

```
OpenCode 调用 simulation_list_tools(tool_name=None 或 "lumerical")
    |
    v
GET /api/simulation/tools
    |
    v
可选: 按 tool_name 过滤
    |
    v
格式化为 Markdown 字符串返回
```

返回示例：

```markdown
# Available Simulation Tools

## lumerical
- **Description**: Lumerical光学仿真工具 - 用于光子学和光学器件仿真
- **Capabilities**: FDTD, MODE, DEVICE, INTERCONNECT

## hyperworks
- **Description**: HyperWorks多物理场仿真工具 - 用于结构、流体、热分析
- **Capabilities**: OptiStruct, Radioss, AcuSolve, Flux
```

---

### 3.5 服务启动入口 (L164-L167)

```python
if __name__ == "__main__":
    print(f"Starting Simulation MCP Server on port {MCP_PORT}...")
    print(f"Java API URL: {JAVA_API_BASE}")
    mcp.run(transport="streamable-http")
```

| 参数 | 说明 |
|------|------|
| `transport="streamable-http"` | 使用 Streamable HTTP 传输协议 |

传输协议说明：

| 协议 | 用途 | 端点 |
|------|------|------|
| `stdio` | 本地进程通信 | 标准输入输出 |
| `sse` | 旧版 SSE 模式 | `/sse` + `/messages/` |
| `streamable-http` | 新版 HTTP 模式 | `/mcp` |

选择 `streamable-http` 的原因：
- 支持远程访问（OpenCode 可通过网络连接）
- 基于标准 HTTP，兼容性好
- 支持会话管理

---

## 四、MCP 协议交互详解

### 4.1 工具发现流程

```
OpenCode                           MCP Server
    |                                  |
    |  POST /mcp                       |
    |  {                               |
    |    "jsonrpc": "2.0",             |
    |    "id": 1,                      |
    |    "method": "initialize",       |
    |    "params": {                   |
    |      "protocolVersion": "2024-11-05",
    |      "clientInfo": {"name": "opencode"}
    |    }                             |
    |  }                               |
    |  ------------------------------> |
    |                                  |
    |  {                               |
    |    "result": {                   |
    |      "serverInfo": {             |
    |        "name": "simulation_mcp"  |
    |      },                          |
    |      "capabilities": {           |
    |        "tools": {}               |
    |      }                           |
    |    }                             |
    |  }                               |
    |  <------------------------------ |
    |                                  |
    |  POST /mcp                       |
    |  {"method": "tools/list"}        |
    |  ------------------------------> |
    |                                  |
    |  返回 3 个工具定义                |
    |  <------------------------------ |
```

### 4.2 工具调用流程

```
OpenCode                           MCP Server                        Java API
    |                                  |                                |
    |  POST /mcp                       |                                |
    |  {                               |                                |
    |    "method": "tools/call",       |                                |
    |    "params": {                   |                                |
    |      "name": "simulation_execute"|                                |
    |      "arguments": {              |                                |
    |        "tool": "lumerical",      |                                |
    |        "action": "FDTD"          |                                |
    |      }                           |                                |
    |    }                             |                                |
    |  }                               |                                |
    |  ------------------------------> |                                |
    |                                  |                                |
    |                                  |  POST /api/simulation/execute  |
    |                                  |  {"tool":"lumerical","action":"FDTD"}
    |                                  |  -----------------------------> |
    |                                  |                                |
    |                                  |  {"status":"success","result":..}
    |                                  |  <----------------------------- |
    |                                  |                                |
    |  {"result":{"content":[{"type":"text","text":"..."}]}}
    |  <------------------------------ |
```

---

## 五、配置参数说明

### 5.1 命令行参数

| 参数 | 默认值 | 说明 | Java 启动命令 |
|------|--------|------|--------------|
| `--port` | 3000 | MCP 服务端口 | `--port 3000` |
| `--java-url` | http://localhost:8080 | Java API 地址 | `--java-url http://localhost:8080` |

### 5.2 环境变量

当前实现不使用环境变量，所有配置通过命令行参数传入。这是由 Java 的 `McpServerLauncher` 通过 `ProcessBuilder` 传递的。

---

## 六、错误处理策略

```
异常发生
    |
    +-- HTTP 404 --> "API endpoint not found"
    |
    +-- HTTP 500 --> "Java server internal error"
    |
    +-- HTTP 其他 --> "API request failed with status {code}"
    |
    +-- ConnectError --> "Cannot connect to Java simulation server"
    |
    +-- TimeoutException --> "Request timed out"
    |
    +-- 其他异常 --> "{ExceptionType}: {message}"
```

所有错误都返回字符串格式，不会抛出异常到 MCP 协议层，确保 OpenCode 始终能收到响应。
