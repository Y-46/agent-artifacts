#!/usr/bin/env node
/**
 * dsh-session-extract.js — 从 DSH 会话 JSONL 日志提取对话脉络
 *
 * 模式（--mode）：
 *   user      仅真实用户消息（source.kind === 'user'，排除插件注入的 system-reminder）
 *   assistant 仅助手文本消息（过滤 tool-call/usage 等结构化块）
 *   all       两者（默认）
 *
 * 选项：
 *   --min-turn N   只输出 turn >= N 的内容
 *   --max-len N    单条截断长度（默认 800 字符）
 *
 * 用法：
 *   node dsh-session-extract.js <session.jsonl> --mode user --min-turn 30 > 脉络.txt
 *
 * 依赖：Node 18+（无第三方依赖）。
 */
'use strict'

const fs = require('fs')
const readline = require('readline')

const args = process.argv.slice(2)
const src = args[0]
if (!src || !fs.existsSync(src)) {
  console.error('用法: node dsh-session-extract.js <session.jsonl> [--mode user|assistant|all] [--min-turn N] [--max-len N]')
  process.exit(2)
}
let mode = 'all'
let minTurn = 0
let maxLen = 800
for (let i = 1; i < args.length; i++) {
  if (args[i] === '--mode') mode = args[++i]
  else if (args[i] === '--min-turn') minTurn = Number(args[++i]) || 0
  else if (args[i] === '--max-len') maxLen = Number(args[++i]) || 800
}

const rl = readline.createInterface({ input: fs.createReadStream(src), crlfDelay: Infinity })
let lineNo = 0
rl.on('line', (line) => {
  lineNo++
  let obj
  try { obj = JSON.parse(line) } catch { return }
  if (mode === 'user' || mode === 'all') {
    if (obj.type === 'user/message' && obj.data?.source?.kind === 'user') {
      const c = obj.data?.content
      if (Array.isArray(c)) {
        for (const part of c) {
          if (part.type === 'text' && part.text) {
            console.log(`[用户 L${lineNo}] ${part.text.slice(0, maxLen).replace(/\s+/g, ' ')}`)
          }
        }
      }
    }
  }
  if (mode === 'assistant' || mode === 'all') {
    if (obj.type === 'assistant/message') {
      const turn = obj.data?.turn
      if (turn !== undefined && turn < minTurn) return
      const c = obj.data?.message?.content
      if (Array.isArray(c)) {
        for (const part of c) {
          if (part.type === 'text' && part.text) {
            console.log(`[助手 T${turn} L${lineNo}] ${part.text.slice(0, maxLen).replace(/\s+/g, ' ')}`)
          }
        }
      }
    }
  }
})
rl.on('close', () => {
  console.error(`[dsh-session-extract] done, ${lineNo} lines scanned`)
})
