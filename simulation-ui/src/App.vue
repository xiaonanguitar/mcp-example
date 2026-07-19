<template>
  <div class="app-container">
    <header class="app-header">
      <div class="header-left">
        <div class="logo">AI 仿真助手</div>
        <span class="subtitle">基于 MCP 协议的智能仿真平台</span>
      </div>
      <div class="header-right">
        <div class="status-dot" :class="{ connected: opencodeConnected }"></div>
        <span class="status-text">{{ opencodeConnected ? 'OpenCode 已连接' : 'OpenCode 未连接' }}</span>
      </div>
    </header>

    <div class="main-content">
      <aside class="sidebar">
        <div class="sidebar-section">
          <h3>仿真工具</h3>
          <div class="tool-list">
            <div class="tool-item">
              <div class="tool-icon">Optical</div>
              <div class="tool-info">
                <div class="tool-name">lumerical</div>
                <div class="tool-desc">光学仿真工具</div>
              </div>
            </div>
            <div class="tool-item">
              <div class="tool-icon">Struct</div>
              <div class="tool-info">
                <div class="tool-name">hyperworks</div>
                <div class="tool-desc">多物理场仿真</div>
              </div>
            </div>
          </div>
        </div>

        <div class="sidebar-section">
          <h3>快捷操作</h3>
          <div class="quick-actions">
            <button
              v-for="action in quickActions"
              :key="action.label"
              class="quick-btn"
              @click="sendQuickAction(action)"
            >
              {{ action.label }}
            </button>
          </div>
        </div>
      </aside>

      <main class="chat-area">
        <ChatPanel ref="chatPanel" />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ChatPanel from './components/ChatPanel.vue'

const opencodeConnected = ref(false)
const chatPanel = ref(null)

const quickActions = [
  { label: '查看可用工具', message: '请列出所有可用的仿真工具及其能力' },
  { label: 'Lumerical FDTD', message: '使用 lumerical 工具执行 FDTD 仿真' },
  { label: 'HyperWorks 结构分析', message: '使用 hyperworks 工具执行 OptiStruct 结构仿真' },
]

async function checkOpenCode() {
  try {
    const res = await fetch('/event', { method: 'HEAD' })
    opencodeConnected.value = res.ok || res.status === 200
  } catch {
    opencodeConnected.value = false
  }
}

function sendQuickAction(action) {
  if (chatPanel.value) {
    chatPanel.value.sendMessage(action.message)
  }
}

onMounted(checkOpenCode)
</script>

<style scoped>
.app-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: #1a1a24;
  border-bottom: 1px solid #2a2a3a;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  font-size: 18px;
  font-weight: 700;
  color: #7c6fff;
}

.subtitle {
  font-size: 12px;
  color: #666;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4444;
  transition: background 0.3s;
}

.status-dot.connected {
  background: #44ff88;
  box-shadow: 0 0 6px #44ff8855;
}

.status-text {
  font-size: 12px;
  color: #888;
}

.main-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: 260px;
  background: #14141e;
  border-right: 1px solid #2a2a3a;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  overflow-y: auto;
  flex-shrink: 0;
}

.sidebar-section h3 {
  font-size: 13px;
  color: #888;
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 12px;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid transparent;
}

.tool-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #2a2a4a;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: #7c6fff;
  flex-shrink: 0;
}

.tool-info {
  overflow: hidden;
}

.tool-name {
  font-size: 14px;
  font-weight: 600;
  color: #ddd;
}

.tool-desc {
  font-size: 11px;
  color: #666;
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.quick-btn {
  padding: 8px 12px;
  background: #1e1e2e;
  border: 1px solid #2a2a3a;
  border-radius: 6px;
  color: #bbb;
  font-size: 13px;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
}

.quick-btn:hover {
  background: #2a2a3e;
  border-color: #7c6fff44;
  color: #fff;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
