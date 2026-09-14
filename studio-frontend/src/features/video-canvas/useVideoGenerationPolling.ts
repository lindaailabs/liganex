import { useEffect, useState } from 'react';
import { getVideoGeneration, type VideoGenerationTask } from '../../api/videoGeneration';
import { extractError } from '../../api/client';

export interface PollingOptions {
  /** 两次回源之间的间隔；默认 3s。 */
  intervalMs?: number;
  /** 次数上限：即使状态始终不是终态也会停止，避免未知状态导致永久轮询。 */
  maxAttempts?: number;
}

export interface PollingState {
  task: VideoGenerationTask | null;
  error: string | null;
  polling: boolean;
}

/** 是否应当继续轮询——抽成纯函数以便直接断言「终态即停」。 */
export function shouldKeepPolling(
  task: VideoGenerationTask | null,
  attempts: number,
  maxAttempts: number,
): boolean {
  if (attempts >= maxAttempts) return false;
  if (!task) return true;
  return !task.terminal;
}

/**
 * 轮询视频生成任务直到终态。
 *
 * 停止条件：任务进入终态（后端统一状态机）、查询失败、或达到次数上限。
 * 终态后不再发起任何请求，避免读取放大上游调用。
 */
export function useVideoGenerationPolling(
  taskId: string | null,
  options: PollingOptions = {},
): PollingState {
  const { intervalMs = 3000, maxAttempts = 100 } = options;
  const [task, setTask] = useState<VideoGenerationTask | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [polling, setPolling] = useState(false);

  useEffect(() => {
    if (!taskId) {
      setTask(null);
      setError(null);
      setPolling(false);
      return;
    }

    let cancelled = false;
    let attempts = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;

    setError(null);
    setPolling(true);

    const tick = async () => {
      try {
        const latest = await getVideoGeneration(taskId);
        if (cancelled) return;
        setTask(latest);
        attempts += 1;
        if (shouldKeepPolling(latest, attempts, maxAttempts)) {
          timer = setTimeout(tick, intervalMs);
        } else {
          setPolling(false);
        }
      } catch (e) {
        if (cancelled) return;
        setError(extractError(e));
        setPolling(false);
      }
    };

    void tick();

    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [taskId, intervalMs, maxAttempts]);

  return { task, error, polling };
}
