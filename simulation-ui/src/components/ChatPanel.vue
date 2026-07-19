<template>
  <div class="chat-panel">
    <div class="messages-container" ref="messagesContainer">
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">AI</div>
        <h2>AI 仿真助手</h2>
        <p>通过自然语言描述您的仿真需求，AI 将自动调用合适的仿真工具执行任务。</p>
        <div class="example-prompts">
          <div class="example" @click="sendMessage('帮我用 Lumerical FDTD 仿真一个 1550nm 波长的波导结构')">
            光学波导仿真
          </div>
          <div class="example" @click="sendMessage('使用 HyperWorks OptiStruct 对一个悬臂梁进行结构优化')">
            结构优化分析
          </div>
          <div class="example" @click="sendMessage('请列出所有可用的仿真工具')">
            查看可用工具
          </div>
        </div>
      </div>

      <div v-for="(msg, idx) in messages" :key="idx" class="message" :class="msg.role">
        <div class="message-avatar">
          {{ msg.role === 'user' ? 'You' : 'AI' }}
        </div>
        <div class="message-content">
          <div class="message-text" v-html="renderMarkdown(msg.content)"></div>
          <div v-if="msg.toolCalls && msg.toolCalls.length" class="tool-calls">
            <div v-for="(tc, i) in msg.toolCalls" :key="i" class="tool-call" :class="'tool-' + tc.status">
              <div class="tool-call-header">
                <span class="tool-call-icon">{{ tc.status === 'completed' ? '✓' : tc.status === 'running' ? '⟳' : '○' }}</span>
                <span>{{ tc.name }}</span>
                <span class="tool-status">{{ tc.status }}</span>
              </div>
              <pre v-if="tc.arguments" class="tool-call-params">{{ tc.arguments }}</pre>
              <pre v-if="tc.output" class="tool-call-output">{{ tc.output }}</pre>
            </div>
          </div>
          <div v-if="msg.loading" class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>

    <div class="input-area">
      <div class="input-wrapper">
        <textarea
          ref="inputRef"
          v-model="inputText"
          @keydown.enter.exact="handleEnter"
          placeholder="描述您的仿真需求..."
          rows="1"
          :disabled="isStreaming"
        ></textarea>
        <button class="send-btn" @click="handleSend" :disabled="!inputText.trim() || isStreaming">
          <span v-if="isStreaming" class="stop-icon" @click.stop="abortSession">Stop</span>
          <span v-else>Send</span>
        </button>
      </div>
      <div class="input-hint">Enter 发送 · Shift+Enter 换行</div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'

const messages = ref([])
const inputText = ref('')
const isStreaming = ref(false)
const messagesContainer = ref(null)
const inputRef = ref(null)

let sessionId = null
let eventSource = null
let currentAiMsgIdx = null
const messageMap = {}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

function renderMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="lang-$1">$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}

// Create a new session
async function createSession() {
  try {
    const res = await fetch('/session', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({})
    })
    if (res.ok) {
      const data = await res.json()
      sessionId = data.id || data.info?.id
      console.log('Session created:', sessionId)
      return sessionId
    }
  } catch (e) {
    console.error('Failed to create session:', e)
  }
  return null
}

// Connect to SSE event stream
function connectSSE() {
  if (eventSource) {
    eventSource.close()
  }

  eventSource = new EventSource('/event')

  eventSource.onopen = () => {
    console.log('SSE connected')
  }

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      handleSSEEvent(data)
    } catch (e) {
      // heartbeat or non-JSON
    }
  }

  eventSource.onerror = (e) => {
    console.error('SSE error:', e)
  }
}

function handleSSEEvent(event) {
  const { type, properties } = event

  // Track new assistant message creation
  if (type === 'message.updated') {
    const info = properties?.info
    if (info?.role === 'assistant') {
      if (!messageMap[info.id]) {
        const aiMsg = { role: 'assistant', content: '', loading: true, toolCalls: [], messageId: info.id }
        messages.value.push(aiMsg)
        messageMap[info.id] = messages.value.length - 1
        scrollToBottom()
      }
      // Detect completion - only if this message is still the current one
      if (info?.finish) {
        const idx = messageMap[info.id]
        if (idx !== undefined && messages.value[idx]) {
          messages.value[idx].loading = false
        }
        // Only stop streaming if no newer message is active
        if (currentAiMsgIdx === messageMap[info.id]) {
          isStreaming.value = false
          currentAiMsgIdx = null
        }
        scrollToBottom()
      }
    }
  }

  // Handle streaming text deltas
  if (type === 'message.part.delta') {
    const msgId = properties?.messageID
    const idx = messageMap[msgId]
    if (idx !== undefined && properties?.field === 'text') {
      messages.value[idx].content += properties.delta
      messages.value[idx].loading = false
      scrollToBottom()
    }
  }

  // Handle tool calls and part updates
  if (type === 'message.part.updated') {
    const part = properties?.part
    const msgId = part?.messageID
    const idx = messageMap[msgId]
    if (idx === undefined) return

    // Tool call
    if (part?.type === 'tool') {
      const state = part.state || {}
      const tc = {
        name: part.tool || 'unknown',
        arguments: '',
        status: state.status || 'pending'
      }
      if (state.input) {
        tc.arguments = typeof state.input === 'string'
          ? state.input
          : JSON.stringify(state.input, null, 2)
      }
      if (state.output) {
        tc.output = typeof state.output === 'string'
          ? state.output
          : JSON.stringify(state.output, null, 2)
      }
      const msg = messages.value[idx]
      // Avoid duplicate tool calls
      const exists = msg.toolCalls.find(t => t.name === tc.name && t.status !== 'completed')
      if (exists) {
        Object.assign(exists, tc)
      } else {
        msg.toolCalls.push(tc)
      }
      msg.loading = false
      scrollToBottom()
    }

    // Full text part update (non-delta)
    if (part?.type === 'text' && part?.text) {
      messages.value[idx].content = part.text
      messages.value[idx].loading = false
      scrollToBottom()
    }
  }

  if (type === 'session.error') {
    console.error('Session error:', properties)
    if (currentAiMsgIdx !== null) {
      messages.value[currentAiMsgIdx].loading = false
      messages.value[currentAiMsgIdx].content += '\n\n[会话错误]'
    }
    isStreaming.value = false
    currentAiMsgIdx = null
  }
}

async function handleEnter(e) {
  if (!e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

async function handleSend() {
  if (!inputText.value.trim() || isStreaming.value) return
  const text = inputText.value.trim()
  inputText.value = ''
  await sendMessage(text)
}

async function sendMessage(text) {
  if (!text || isStreaming.value) return

  // Ensure session exists
  if (!sessionId) {
    await createSession()
    if (!sessionId) {
      alert('无法创建会话，请确保 OpenCode 服务已启动')
      return
    }
  }

  // Add user message
  messages.value.push({ role: 'user', content: text })
  scrollToBottom()

  isStreaming.value = true

  try {
    const res = await fetch(`/session/${sessionId}/message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        parts: [{ type: 'text', text }]
      })
    })

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${res.statusText}`)
    }
    // Response streams via SSE — nothing more to do here

  } catch (error) {
    // If SSE didn't create an AI message, create one
    if (currentAiMsgIdx === null) {
      const aiMsg = { role: 'assistant', content: `请求失败: ${error.message}`, loading: false, toolCalls: [] }
      messages.value.push(aiMsg)
    }
    isStreaming.value = false
  } finally {
    scrollToBottom()
  }
}

async function abortSession() {
  if (!sessionId) return
  try {
    await fetch(`/session/${sessionId}/abort`, { method: 'POST' })
  } catch (e) {
    console.error('Abort failed:', e)
  }
  if (currentAiMsgIdx !== null && messages.value[currentAiMsgIdx]) {
    messages.value[currentAiMsgIdx].loading = false
  }
  isStreaming.value = false
  currentAiMsgIdx = null
}

onMounted(() => {
  connectSSE()
})

onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #0f0f14;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  scroll-behavior: smooth;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 16px;
}

.empty-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: linear-gradient(135deg, #7c6fff, #5b4cd4);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 800;
  color: #fff;
}

.empty-state h2 {
  font-size: 22px;
  color: #eee;
}

.empty-state p {
  color: #666;
  font-size: 14px;
  max-width: 400px;
  text-align: center;
}

.example-prompts {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.example {
  padding: 8px 16px;
  background: #1a1a28;
  border: 1px solid #2a2a3a;
  border-radius: 8px;
  font-size: 13px;
  color: #aaa;
  cursor: pointer;
  transition: all 0.2s;
}

.example:hover {
  background: #22223a;
  border-color: #7c6fff55;
  color: #ddd;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  max-width: 800px;
}

.message.user {
  margin-left: auto;
  flex-direction: row-reverse;
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: #333;
  color: #aaa;
}

.message.assistant .message-avatar {
  background: linear-gradient(135deg, #7c6fff, #5b4cd4);
  color: #fff;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-text {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  word-wrap: break-word;
}

.message.user .message-text {
  background: #7c6fff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message.assistant .message-text {
  background: #1a1a28;
  color: #ddd;
  border-bottom-left-radius: 4px;
}

.message-text :deep(code) {
  background: #2a2a3a;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.message-text :deep(pre) {
  background: #0a0a12;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
}

.tool-calls {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-call {
  background: #12121c;
  border: 1px solid #2a2a3a;
  border-radius: 8px;
  overflow: hidden;
}

.tool-call-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #1a1a28;
  border-bottom: 1px solid #2a2a3a;
  font-size: 12px;
  color: #7c6fff;
  font-weight: 600;
}

.tool-status {
  margin-left: auto;
  font-weight: 400;
  font-size: 11px;
  color: #666;
  text-transform: uppercase;
}

.tool-running .tool-call-header {
  border-left: 3px solid #f0a030;
}

.tool-completed .tool-call-header {
  border-left: 3px solid #4caf50;
}

.tool-call-icon {
  background: #7c6fff22;
  padding: 2px 6px;
  border-radius: 4px;
}

.tool-call-params {
  padding: 10px 12px;
  font-size: 12px;
  color: #888;
  overflow-x: auto;
  margin: 0;
}

.tool-call-output {
  padding: 10px 12px;
  font-size: 12px;
  color: #4caf50;
  overflow-x: auto;
  margin: 0;
  border-top: 1px solid #2a2a3a;
  background: #0d1a0d;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 12px 16px;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #555;
  animation: bounce 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(1) { animation-delay: 0s; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.input-area {
  padding: 16px 20px;
  background: #14141e;
  border-top: 1px solid #2a2a3a;
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: #1a1a28;
  border: 1px solid #2a2a3a;
  border-radius: 12px;
  padding: 8px 12px;
  transition: border-color 0.2s;
}

.input-wrapper:focus-within {
  border-color: #7c6fff66;
}

.input-wrapper textarea {
  flex: 1;
  background: transparent;
  border: none;
  color: #ddd;
  font-size: 14px;
  font-family: inherit;
  resize: none;
  outline: none;
  min-height: 24px;
  max-height: 120px;
}

.input-wrapper textarea::placeholder {
  color: #555;
}

.input-wrapper textarea:disabled {
  opacity: 0.5;
}

.send-btn {
  padding: 6px 16px;
  background: #7c6fff;
  border: none;
  border-radius: 8px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.send-btn:hover:not(:disabled) {
  background: #6a5bff;
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.stop-icon {
  color: #ff6666;
}

.input-hint {
  font-size: 11px;
  color: #444;
  margin-top: 6px;
  text-align: center;
}
</style>
