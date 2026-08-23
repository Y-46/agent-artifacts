#!/usr/bin/env node
/**
 * dsh-session-decompress.js — 解压 DSH 会话日志（session.jsonl.zstd）
 *
 * DSH 的会话持久化采用「多帧 concatenated zstd」容器：一个带校验和的
 * header 帧 + 若干追加帧。Node 的 zstdDecompressSync 一次性解压只能拿到
 * 第一帧，因此必须按 zstd 帧结构逐帧扫描、逐帧解压后拼接。
 *
 * 用法：
 *   node dsh-session-decompress.js <session.jsonl.zstd> [输出路径]
 *   # 默认输出：<输入去掉 .zstd 后缀>
 *
 * 依赖：Node 22.15+（node:zlib 内置 zstd）。
 */
'use strict'

const fs = require('fs')
const { zstdDecompressSync } = require('node:zlib')

const src = process.argv[2]
const dst = process.argv[3] || src.replace(/\.zstd$/, '')
if (!src) {
  console.error('用法: node dsh-session-decompress.js <session.jsonl.zstd> [输出路径]')
  process.exit(2)
}

const buf = fs.readFileSync(src)
const ZSTD_MAGIC = 0xFD2FB528

/** 结构扫描 concatenated zstd 流，返回完整帧区间列表（不实际解压）。 */
function scanZstdFrames(buffer) {
  const frames = []
  let offset = 0
  while (offset < buffer.length) {
    const start = offset
    if (buffer.length - offset < 4) return { frames, tornStart: start }
    if (buffer.readUInt32LE(offset) !== ZSTD_MAGIC) {
      throw new Error(`invalid frame magic at byte ${offset}`)
    }
    offset += 4
    const descriptor = buffer.readUInt8(offset++)
    if ((descriptor & 0x18) !== 0) throw new Error('reserved frame-header bit set')
    const contentSizeFlag = descriptor >>> 6
    const singleSegment = (descriptor & 0x20) !== 0
    const checksum = (descriptor & 0x04) !== 0
    const dictionaryFlag = descriptor & 0x03
    const dictionaryBytes = dictionaryFlag === 3 ? 4 : dictionaryFlag
    const contentSizeBytes =
      contentSizeFlag === 0 ? (singleSegment ? 1 : 0) : (1 << contentSizeFlag)
    const remainingHeaderBytes = (singleSegment ? 0 : 1) + dictionaryBytes + contentSizeBytes
    if (buffer.length - offset < remainingHeaderBytes) return { frames, tornStart: start }
    offset += remainingHeaderBytes
    for (;;) {
      if (buffer.length - offset < 3) return { frames, tornStart: start }
      const blockHeader = buffer.readUIntLE(offset, 3)
      offset += 3
      const lastBlock = (blockHeader & 1) !== 0
      const blockType = (blockHeader >>> 1) & 0x03
      const blockSize = blockHeader >>> 3
      if (blockType === 0x03) throw new Error(`reserved block type at byte ${offset - 3}`)
      const payloadBytes = blockType === 0x01 ? 1 : blockSize
      if (buffer.length - offset < payloadBytes) return { frames, tornStart: start }
      offset += payloadBytes
      if (lastBlock) break
    }
    if (checksum) {
      if (buffer.length - offset < 4) return { frames, tornStart: start }
      offset += 4
    }
    frames.push({ start, end: offset })
  }
  return { frames }
}

const { frames, tornStart } = scanZstdFrames(buf)
const parts = []
for (const f of frames) {
  parts.push(zstdDecompressSync(buf.subarray(f.start, f.end)))
}
const out = Buffer.concat(parts)
fs.writeFileSync(dst, out)
console.log(
  `frames: ${frames.length}${tornStart !== undefined ? `, torn at ${tornStart}` : ''}, ` +
    `decoded ${out.length} bytes -> ${dst}`
)
