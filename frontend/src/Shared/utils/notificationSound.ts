let sharedCtx: AudioContext | null = null

function getAudioContext(): AudioContext | null {
  const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  if (!Ctx) return null
  if (!sharedCtx) sharedCtx = new Ctx()
  return sharedCtx
}

/** Dois bipes curtos para alerta de evento. */
export function playScheduleAlertSound() {
  const ctx = getAudioContext()
  if (!ctx) return

  const run = () => {
    const beep = (freq: number, start: number, duration: number) => {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = freq
      osc.connect(gain)
      gain.connect(ctx.destination)
      gain.gain.setValueAtTime(0.0001, start)
      gain.gain.exponentialRampToValueAtTime(0.12, start + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, start + duration)
      osc.start(start)
      osc.stop(start + duration + 0.02)
    }
    const t = ctx.currentTime
    beep(880, t, 0.14)
    beep(1175, t + 0.2, 0.18)
  }

  if (ctx.state === 'suspended') {
    void ctx.resume().then(run)
  } else {
    run()
  }
}
