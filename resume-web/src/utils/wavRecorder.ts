/** 将 Float32 音频采样转换为 16 位 PCM */
function floatTo16BitPCM(samples: Float32Array): Int16Array {
  const pcm = new Int16Array(samples.length)
  for (let i = 0; i < samples.length; i++) {
    const sample = Math.max(-1, Math.min(1, samples[i] ?? 0))
    pcm[i] = sample < 0 ? sample * 0x8000 : sample * 0x7fff
  }
  return pcm
}

export interface WavRecorderOptions {
  /** 采样率，默认 16000 Hz */
  sampleRate?: number
  /** PCM 数据块回调，用于 WebSocket 实时发送 */
  onChunk: (pcmBuffer: ArrayBuffer) => void
  onError?: (error: unknown) => void
}

/**
 * 基于 getUserMedia + AudioContext 采集音频，
 * 以 16-bit PCM 格式分块输出（适用于 WAV 流式传输）
 */
export class WavRecorder {
  private stream: MediaStream | null = null
  private audioContext: AudioContext | null = null
  private source: MediaStreamAudioSourceNode | null = null
  private processor: ScriptProcessorNode | null = null
  private recording = false

  async start(options: WavRecorderOptions): Promise<void> {
    if (this.recording) {
      return
    }

    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const sampleRate = options.sampleRate ?? 16000
      this.audioContext = new AudioContext({ sampleRate })
      this.source = this.audioContext.createMediaStreamSource(this.stream)
      this.processor = this.audioContext.createScriptProcessor(4096, 1, 1)

      this.processor.onaudioprocess = (event) => {
        if (!this.recording) {
          return
        }
        const channelData = event.inputBuffer.getChannelData(0)
        const pcm = floatTo16BitPCM(channelData)
        const buffer = new ArrayBuffer(pcm.byteLength)
        new Int16Array(buffer).set(pcm)
        options.onChunk(buffer)
      }

      this.source.connect(this.processor)
      this.processor.connect(this.audioContext.destination)
      this.recording = true
    } catch (error) {
      options.onError?.(error)
      throw error
    }
  }

  stop(): void {
    this.recording = false
    this.processor?.disconnect()
    this.source?.disconnect()
    this.processor = null
    this.source = null

    this.stream?.getTracks().forEach((track) => track.stop())
    this.stream = null

    if (this.audioContext) {
      void this.audioContext.close()
      this.audioContext = null
    }
  }

  isRecording(): boolean {
    return this.recording
  }
}
