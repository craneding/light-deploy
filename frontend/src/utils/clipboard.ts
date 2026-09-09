/**
 * 安全复制文本到剪贴板。
 * navigator.clipboard 仅在安全上下文（https / localhost）可用，
 * http 部署时为 undefined，直接调用会抛
 * TypeError: Cannot read properties of undefined (reading 'writeText')。
 * 失败时降级为 textarea + execCommand('copy')。
 */
export async function copyText(text: string): Promise<void> {
  const content = text ?? ''
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(content)
      return
    }
  } catch {
    // clipboard API 拒绝（如无权限）则继续走降级路径
  }

  return new Promise<void>((resolve, reject) => {
    try {
      const ta = document.createElement('textarea')
      ta.value = content
      ta.setAttribute('readonly', '')
      ta.style.position = 'fixed'
      ta.style.top = '-9999px'
      ta.style.left = '-9999px'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.focus()
      ta.select()
      ta.setSelectionRange(0, ta.value.length)
      const ok = document.execCommand('copy')
      document.body.removeChild(ta)
      if (ok) {
        resolve()
      } else {
        reject(new Error('复制失败，浏览器拒绝了剪贴板写入'))
      }
    } catch (err) {
      reject(err instanceof Error ? err : new Error('复制失败'))
    }
  })
}
