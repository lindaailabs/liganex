import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getVideoGeneration, type VideoGenerationTask } from '../../api/videoGeneration';
import { shouldKeepPolling, useVideoGenerationPolling } from './useVideoGenerationPolling';

vi.mock('../../api/videoGeneration', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/videoGeneration')>();
  return { ...actual, getVideoGeneration: vi.fn() };
});

const getMock = vi.mocked(getVideoGeneration);

function task(status: VideoGenerationTask['status']): VideoGenerationTask {
  return {
    id: '7',
    provider: 'openai',
    model: 'sora-2',
    status,
    terminal: status === 'SUCCEEDED' || status === 'FAILED',
    prompt: '一只猫',
    createdAt: '2026-09-13T00:00:00Z',
    updatedAt: '2026-09-13T00:00:00Z',
  };
}

beforeEach(() => {
  // 每个用例都要从干净的调用计数开始：本文件按「调用了几次」断言轮询行为，
  // 计数一旦跨用例累加，断言就会失真。
  vi.resetAllMocks();
});

afterEach(() => {
  // vitest 未开 globals，RTL 的自动 cleanup 不会注册：不手动卸载的话，
  // 上一个用例的 hook 会继续持有定时器（并用新用例的 mock 实现）发请求。
  cleanup();
  vi.useRealTimers();
});

describe('shouldKeepPolling', () => {
  it('keeps polling while the task is not terminal', () => {
    expect(shouldKeepPolling(task('PENDING'), 1, 10)).toBe(true);
    expect(shouldKeepPolling(task('RUNNING'), 1, 10)).toBe(true);
  });

  it('stops on terminal statuses', () => {
    expect(shouldKeepPolling(task('SUCCEEDED'), 1, 10)).toBe(false);
    expect(shouldKeepPolling(task('FAILED'), 1, 10)).toBe(false);
  });

  it('stops at the attempt ceiling so an unknown status cannot poll forever', () => {
    expect(shouldKeepPolling(task('RUNNING'), 10, 10)).toBe(false);
  });

  it('keeps polling before the first snapshot arrives', () => {
    expect(shouldKeepPolling(null, 0, 10)).toBe(true);
  });
});

describe('useVideoGenerationPolling', () => {
  it('polls a running task until it reaches a terminal state, then stops', async () => {
    vi.useFakeTimers();
    getMock.mockResolvedValueOnce(task('RUNNING')).mockResolvedValueOnce(task('SUCCEEDED'));

    const { result } = renderHook(() =>
      useVideoGenerationPolling('7', { intervalMs: 1000, maxAttempts: 10 }),
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.task?.status).toBe('RUNNING');
    expect(result.current.polling).toBe(true);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(result.current.task?.status).toBe('SUCCEEDED');
    expect(result.current.polling).toBe(false);

    // 终态之后不再回源：这是「读取不放大上游调用」的前端一半
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(getMock).toHaveBeenCalledTimes(2);
  });

  it('stops immediately when the very first snapshot is already terminal', async () => {
    vi.useFakeTimers();
    getMock.mockResolvedValue(task('FAILED'));

    const { result } = renderHook(() => useVideoGenerationPolling('7', { intervalMs: 1000 }));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.task?.status).toBe('FAILED');
    expect(result.current.polling).toBe(false);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(getMock).toHaveBeenCalledTimes(1);
  });

  it('stops and surfaces a readable error when a poll fails', async () => {
    vi.useFakeTimers();
    getMock.mockRejectedValue(new Error('网络请求失败'));

    const { result } = renderHook(() => useVideoGenerationPolling('7', { intervalMs: 1000 }));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.error).toBe('网络请求失败');
    expect(result.current.polling).toBe(false);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(getMock).toHaveBeenCalledTimes(1);
  });

  it('gives up after maxAttempts even if the task never settles', async () => {
    vi.useFakeTimers();
    getMock.mockResolvedValue(task('RUNNING'));

    const { result } = renderHook(() =>
      useVideoGenerationPolling('7', { intervalMs: 1000, maxAttempts: 3 }),
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });

    expect(getMock).toHaveBeenCalledTimes(3);
    expect(result.current.polling).toBe(false);
  });

  it('does nothing without a task id', async () => {
    const { result } = renderHook(() => useVideoGenerationPolling(null));

    await waitFor(() => expect(result.current.polling).toBe(false));
    expect(getMock).not.toHaveBeenCalled();
    expect(result.current.task).toBeNull();
  });
});
