import { onUnmounted, ref, watch } from 'vue'
import type { Ref } from 'vue'

/** 自动刷新间隔：3s，兼顾状态实时性与后端压力 */
export const AUTO_REFRESH_INTERVAL_MS = 3000
/** 连续失败熔断次数：超过后自动停止轮询，避免网络异常时持续打请求 */
const MAX_CONSECUTIVE_FAILURES = 3

const pad = (value: number) => String(value).padStart(2, '0')
const formatClock = (date: Date) =>
  `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`

/**
 * 列表 / 看板页自动刷新：
 * - 仅在「开关开启 + 页面可见 + 存在未完成任务」时按固定间隔静默刷新
 * - 页面隐藏时暂停，回到前台立即补一次
 * - 连续失败达到上限后自动停止，避免持续刷请求与错误提示
 *
 * @param hasActive 是否存在未完成任务（传 getter，便于直接使用 computed）
 * @param refresh 静默刷新函数，返回本次是否成功
 * @param enabled 自动刷新开关，由调用方创建以便直接用于 v-model
 */
export function useAutoRefresh(
  hasActive: () => boolean,
  refresh: () => Promise<boolean>,
  enabled: Ref<boolean> = ref(true)
) {
  const lastUpdated = ref('')
  const polling = ref(false)

  let timer: ReturnType<typeof setInterval> | null = null
  let inFlight = false
  let failures = 0

  const isVisible = () => document.visibilityState !== 'hidden'

  const stop = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    polling.value = false
  }

  const tick = async () => {
    if (inFlight) return
    inFlight = true
    let ok = false
    try {
      ok = await refresh()
    } catch {
      ok = false
    } finally {
      inFlight = false
    }
    if (ok) {
      failures = 0
      lastUpdated.value = formatClock(new Date())
    } else if (++failures >= MAX_CONSECUTIVE_FAILURES) {
      // 连续失败熔断：直接关闭开关，让用户看到自动刷新已停止（可手动再打开重试）
      stop()
      enabled.value = false
    }
  }

  const start = () => {
    if (timer || !enabled.value || !isVisible() || !hasActive()) return
    polling.value = true
    timer = setInterval(tick, AUTO_REFRESH_INTERVAL_MS)
  }

  const sync = () => {
    if (enabled.value && isVisible() && hasActive()) start()
    else stop()
  }

  const handleVisibilityChange = () => {
    if (!enabled.value) return
    if (!isVisible()) {
      stop()
      return
    }
    if (hasActive()) {
      void tick()
      start()
    }
  }

  const markUpdated = () => {
    lastUpdated.value = formatClock(new Date())
  }

  watch(enabled, sync)
  watch(hasActive, sync)

  document.addEventListener('visibilitychange', handleVisibilityChange)
  onUnmounted(() => {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    stop()
  })

  return { lastUpdated, polling, sync, markUpdated }
}
