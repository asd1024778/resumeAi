/** 语音 WebSocket 默认地址 */
export const VOICE_WS_URL = 'ws://localhost:8080/ws/voice'

export interface VoiceSocketOptions {
  url?: string
  onAudio: (buffer: ArrayBuffer) => void
  onError?: (error: unknown) => void
  onOpen?: () => void
  onClose?: () => void
}

/** 语音 WebSocket 客户端：发送 PCM 音频流，接收 TTS 音频并播放 */
export class VoiceSocket {
  private ws: WebSocket | null = null
  private audioContext: AudioContext | null = null
  private playbackQueue: Promise<void> = Promise.resolve()

  connect(options: VoiceSocketOptions): Promise<void> {
    return new Promise((resolve, reject) => {
      const url = options.url ?? VOICE_WS_URL
      this.ws = new WebSocket(url)
      this.ws.binaryType = 'arraybuffer'

      this.ws.onopen = () => {
        options.onOpen?.()
        resolve()
      }

      this.ws.onmessage = (event) => {
        if (event.data instanceof ArrayBuffer) {
          options.onAudio(event.data)
          this.enqueuePlayback(event.data)
        }
      }

      this.ws.onerror = () => {
        const error = new Error('语音 WebSocket 连接异常')
        options.onError?.(error)
        reject(error)
      }

      this.ws.onclose = () => {
        options.onClose?.()
      }
    })
  }

  send(buffer: ArrayBuffer): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(buffer)
    }
  }

  /** 串行播放 TTS 音频，避免重叠 */
  private enqueuePlayback(buffer: ArrayBuffer): void {
    this.playbackQueue = this.playbackQueue.then(() => this.playAudio(buffer))
  }

  async playAudio(buffer: ArrayBuffer): Promise<void> {
    if (!this.audioContext) {
      this.audioContext = new AudioContext()
    }
    const audioBuffer = await this.audioContext.decodeAudioData(buffer.slice(0))
    await new Promise<void>((resolve) => {
      const source = this.audioContext!.createBufferSource()
      source.buffer = audioBuffer
      source.connect(this.audioContext!.destination)
      source.onended = () => resolve()
      source.start(0)
    })
  }

  close(): void {
    if (this.ws) {
      this.ws.onclose = null
      this.ws.close()
      this.ws = null
    }
    if (this.audioContext) {
      void this.audioContext.close()
      this.audioContext = null
    }
  }

  isOpen(): boolean {
    return this.ws?.readyState === WebSocket.OPEN
  }
}
