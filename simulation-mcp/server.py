#!/usr/bin/env python3
"""
Simulation MCP Server

This MCP server bridges the MCP protocol to the Java Simulation API,
providing simulation tools for OpenCode to interact with.
Transport: Streamable HTTP (SSE mode).
"""

import argparse
import json
import httpx
from typing import Optional
from pydantic import BaseModel, Field, ConfigDict
from mcp.server.fastmcp import FastMCP

parser = argparse.ArgumentParser(description="Simulation MCP Server")
parser.add_argument("--port", type=int, default=3000, help="MCP server port")
parser.add_argument("--java-url", type=str, default="http://localhost:8080", help="Java backend URL")
args, _ = parser.parse_known_args()

JAVA_API_BASE = 'http://localhost:8080'
MCP_PORT = args.port

mcp = FastMCP("simulation_mcp", host="0.0.0.0", port=MCP_PORT)


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


class GetTaskStatusInput(BaseModel):
    """Query the status and results of a simulation task."""
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)

    task_id: str = Field(..., description="The task ID from a previous simulation execution")


class ListToolsInput(BaseModel):
    """List available simulation tools and their capabilities."""
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)

    tool_name: Optional[str] = Field(default=None, description="Optional filter by tool name")


class ListTasksInput(BaseModel):
    """List all active async simulation tasks."""
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)


async def _call_java_api(endpoint: str, method: str = "GET", **kwargs) -> dict:
    async with httpx.AsyncClient(timeout=30.0, trust_env=False) as client:
        response = await client.request(method, f"{JAVA_API_BASE}{endpoint}", **kwargs)
        response.raise_for_status()
        return response.json()


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


@mcp.tool(
    name="simulation_execute",
    annotations={
        "title": "Execute Simulation",
        "readOnlyHint": False,
        "destructiveHint": False,
        "idempotentHint": False,
        "openWorldHint": True
    }
)
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


@mcp.tool(
    name="simulation_execute_async",
    annotations={
        "title": "Execute Async Simulation",
        "readOnlyHint": False,
        "destructiveHint": False,
        "idempotentHint": False,
        "openWorldHint": True
    }
)
async def simulation_execute_async(params: ExecuteSimulationInput) -> str:
    """Submit a physics simulation task for asynchronous execution.

    Returns a taskId that can be used with simulation_get_status to check progress.
    The async simulation will run through these stages:
    - 10%: Initializing simulation environment
    - 30%: Building mesh model
    - 50%: Setting boundary conditions
    - 70%: Solving
    - 90%: Post-processing results
    - 100%: Completed

    Supported tools and actions:
    Lumerical: FDTD, MODE, DEVICE, INTERCONNECT
    HyperWorks: OptiStruct, Radioss, AcuSolve, Flux
    """
    try:
        request_data = {"tool": params.tool, "action": params.action}
        if params.parameters:
            request_data["parameters"] = params.parameters
        result = await _call_java_api("/api/simulation/execute/async", method="POST", json=request_data)
        return json.dumps(result, indent=2, ensure_ascii=False)
    except Exception as e:
        return _format_error(e)


@mcp.tool(
    name="simulation_get_status",
    annotations={
        "title": "Get Task Status",
        "readOnlyHint": True,
        "destructiveHint": False,
        "idempotentHint": True,
        "openWorldHint": True
    }
)
async def simulation_get_status(params: GetTaskStatusInput) -> str:
    """Query the status and results of a previously submitted simulation task.

    Supports both synchronous and asynchronous tasks:
    - Synchronous tasks: Returns the completed result
    - Asynchronous tasks: Returns progress (0-100%) and current stage

    Use simulation_list_tasks to see all active async tasks.
    """
    # Try async task endpoint first
    try:
        result = await _call_java_api(f"/api/simulation/async/task/{params.task_id}")
        if result.get("status") != "not_found":
            lines = [f"# Task Status: {params.task_id}", ""]
            status = result.get("status", "unknown")
            progress = result.get("progress", 0)
            lines.append(f"- **Status**: {status}")
            lines.append(f"- **Progress**: {progress}%")
            if result.get("tool"):
                lines.append(f"- **Tool**: {result['tool']}")
            if result.get("action"):
                lines.append(f"- **Action**: {result['action']}")
            if result.get("message"):
                lines.append(f"- **Message**: {result['message']}")
            if result.get("errorMessage"):
                lines.append(f"- **Error**: {result['errorMessage']}")
            if result.get("result") and status == "completed":
                lines.append("")
                lines.append("## Result")
                lines.append("```json")
                lines.append(json.dumps(result["result"], indent=2, ensure_ascii=False))
                lines.append("```")
            return "\n".join(lines)
    except Exception:
        pass

    # Fall back to sync task endpoint
    try:
        result = await _call_java_api(f"/api/simulation/task/{params.task_id}")
        return json.dumps(result, indent=2, ensure_ascii=False)
    except Exception as e:
        return _format_error(e)


@mcp.tool(
    name="simulation_list_tools",
    annotations={
        "title": "List Simulation Tools",
        "readOnlyHint": True,
        "destructiveHint": False,
        "idempotentHint": True,
        "openWorldHint": False
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


@mcp.tool(
    name="simulation_list_tasks",
    annotations={
        "title": "List Active Tasks",
        "readOnlyHint": True,
        "destructiveHint": False,
        "idempotentHint": True,
        "openWorldHint": True
    }
)
async def simulation_list_tasks(params: ListTasksInput) -> str:
    """List all active async simulation tasks and their progress.

    Returns a list of tasks with their current status, progress percentage,
    and associated tool/action information.
    """
    try:
        result = await _call_java_api("/api/simulation/async/tasks")
        if not result:
            return "No active async tasks."

        lines = ["# Active Simulation Tasks", ""]
        for task in result:
            task_id = task.get("taskId", "unknown")
            status = task.get("status", "unknown")
            progress = task.get("progress", 0)
            tool = task.get("tool", "")
            action = task.get("action", "")
            message = task.get("message", "")
            lines.append(f"## Task {task_id}")
            lines.append(f"- **Status**: {status}")
            lines.append(f"- **Progress**: {progress}%")
            if tool:
                lines.append(f"- **Tool**: {tool}")
            if action:
                lines.append(f"- **Action**: {action}")
            if message:
                lines.append(f"- **Message**: {message}")
            lines.append("")

        return "\n".join(lines)
    except Exception as e:
        return _format_error(e)


if __name__ == "__main__":
    print(f"Starting Simulation MCP Server on port {MCP_PORT}...")
    print(f"Java API URL: {JAVA_API_BASE}")
    mcp.run(transport="streamable-http")
