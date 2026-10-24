/**
 * 一次性反馈动画工具。
 *
 * 为什么要"摘类"：动画只在"类被新加上"时触发。如果类常驻，那么在**减弱动画**期间
 * 触发的这些类（动画被 `animation: none` 干掉、却仍然留在元素上）会一直挂着 ——
 * 用户以后一关掉"减弱动画"，这些动画就会毫无缘由地集体重播一遍。
 */

const pulseTimers = new WeakMap<HTMLElement, number>();

/** 播放一次性反馈动画：先移除类并强制重排，保证连续触发时动画能重新开始 */
export function pulse(node: HTMLElement | null | undefined, cls: string): void {
  if (!node) return;
  node.classList.remove(cls);
  void node.offsetWidth;
  node.classList.add(cls);

  const prev = pulseTimers.get(node);
  if (prev) window.clearTimeout(prev);
  // 比最长的反馈动画（0.34s）留足余量
  pulseTimers.set(
    node,
    window.setTimeout(() => {
      node.classList.remove(cls);
      pulseTimers.delete(node);
    }, 600),
  );
}

/** 复选框点选反馈：给 .cb 加一次性动画类（勾/横杠弹入 + 方框微弹） */
export function popCheckbox(input: HTMLInputElement): void {
  const label = input.closest<HTMLElement>('.cb');
  if (label) pulse(label, 'box-pop');
}
