import { marked } from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true,
})

/** 将 Markdown 文本渲染为 HTML */
export function renderMarkdown(content: string): string {
  if (!content) {
    return ''
  }
  return marked.parse(content, { async: false }) as string
}
