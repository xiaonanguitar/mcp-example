# AI 仿真助手 (Simulation MCP Platform)

基于 MCP 协议的 AI 辅助物理仿真平台，集成 OpenCode 智能体，通过自然语言驱动仿真任务执行。

## 项目架构

```
simulation-server/   # Java Spring Boot 后端 (仿真API + MCP进程管理)
simulation-mcp/      # Python MCP 服务 (SSE模式, 由Java启动)
simulation-ui/       # Vue 3 前端 (流式对话界面)
```

### 数据流

```
用户 -> Vue3前端 -> OpenCode智能体 -> MCP协议 -> Python MCP服务 -> Java仿真API
                                                        ↓
                                                  Mock仿真结果返回
```

## 技术栈

| 组件 | 技术 | 端口 |
|------|------|------|
| Java后端 | Spring Boot 3.2 | 8080 |
| MCP服务 | Python FastMCP (SSE) | 3000 |
| Vue前端 | Vue 3 + Vite | 5173 |

## 快速开始

### 1. 启动 Java 后端

```bash
cd simulation-server
mvn spring-boot:run
```

Java 启动时会自动拉起 Python MCP 服务。

### 2. 启动 Vue 前端

```bash
cd simulation-ui
npm install
npm run dev
```

访问 http://localhost:5173

### 3. 安装 Python MCP 依赖 (可选, Java会自动拉起)

```bash
cd simulation-mcp
pip install -r requirements.txt
```

## API 接口

### 获取仿真工具列表
```
GET /api/simulation/tools
```

### 执行仿真任务
```
POST /api/simulation/execute
{
  "tool": "lumerical",
  "action": "FDTD",
  "parameters": "{\"wavelength\": 1550}"
}
```

### 查询任务状态
```
GET /api/simulation/task/{taskId}
```

## MCP 工具

| 工具名 | 功能 |
|--------|------|
| `simulation_execute` | 执行仿真任务 |
| `simulation_get_status` | 查询任务状态 |
| `simulation_list_tools` | 列出可用工具 |

### 支持的仿真工具

**Lumerical (光学仿真)**
- FDTD: 时域有限差分仿真
- MODE: 波导模式求解
- DEVICE: 器件级仿真
- INTERCONNECT: 光子电路仿真

**HyperWorks (结构/多物理场)**
- OptiStruct: 结构优化
- Radioss: 非线性显式动力学
- AcuSolve: 计算流体力学
- Flux: 电磁/热仿真
