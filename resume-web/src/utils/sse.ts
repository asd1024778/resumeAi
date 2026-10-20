/** 解析 SSE 事件 data 字段，兼容 Spring AI 多种 payload 格式 */
export function parseSseChunk(data: string): string {
  if (!data || data === '[DONE]') {
    return ''
  }

  try {
    const json = JSON.parse(data) as Record<string, unknown>
    const content =
      json.content ?? json.text ?? json.delta ?? json.message ?? json.answer
    return typeof content === 'string' ? content : data
  } catch {
    return data
  }
}
