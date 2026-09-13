import { describe, expect, it } from 'vitest';
import { ApiError } from './client';
import {
  TERMINAL_STATUSES,
  VIDEO_PROVIDER_NOT_CONFIGURED,
  isProviderNotConfigured,
  isTerminalStatus,
  normalizeStatus,
  normalizeVideoTask,
} from './videoGeneration';

describe('status normalization', () => {
  it('passes through the unified statuses issued by the backend', () => {
    expect(normalizeStatus('PENDING')).toBe('PENDING');
    expect(normalizeStatus('RUNNING')).toBe('RUNNING');
    expect(normalizeStatus('SUCCEEDED')).toBe('SUCCEEDED');
    expect(normalizeStatus('FAILED')).toBe('FAILED');
  });

  it('accepts lowercase and padded values', () => {
    expect(normalizeStatus(' running ')).toBe('RUNNING');
    expect(normalizeStatus('succeeded')).toBe('SUCCEEDED');
  });

  it('tolerates common aliases from either side of the boundary', () => {
    expect(normalizeStatus('completed')).toBe('SUCCEEDED');
    expect(normalizeStatus('success')).toBe('SUCCEEDED');
    expect(normalizeStatus('error')).toBe('FAILED');
    expect(normalizeStatus('canceled')).toBe('FAILED');
  });

  it('treats an unrecognized status as in-progress rather than a false failure', () => {
    expect(normalizeStatus('who_knows')).toBe('RUNNING');
    expect(normalizeStatus(undefined)).toBe('RUNNING');
    expect(normalizeStatus(42)).toBe('RUNNING');
  });
});

describe('terminal status set', () => {
  it('contains exactly the terminal statuses', () => {
    expect([...TERMINAL_STATUSES].sort()).toEqual(['FAILED', 'SUCCEEDED']);
  });

  it('classifies statuses', () => {
    expect(isTerminalStatus('SUCCEEDED')).toBe(true);
    expect(isTerminalStatus('FAILED')).toBe(true);
    expect(isTerminalStatus('RUNNING')).toBe(false);
    expect(isTerminalStatus('PENDING')).toBe(false);
  });
});

describe('normalizeVideoTask', () => {
  it('reads the backend snapshot', () => {
    const task = normalizeVideoTask({
      id: 7,
      provider: 'openai',
      model: 'sora-2',
      status: 'PENDING',
      terminal: false,
      prompt: '一只猫',
      createdAt: '2026-09-13T00:00:00Z',
      updatedAt: '2026-09-13T00:00:00Z',
    });

    expect(task).toMatchObject({
      id: '7',
      provider: 'openai',
      model: 'sora-2',
      status: 'PENDING',
      terminal: false,
      prompt: '一只猫',
    });
    expect(task.videoUrl).toBeUndefined();
    expect(task.errorMessage).toBeUndefined();
  });

  it('derives terminal from the status even if the flag is missing', () => {
    expect(normalizeVideoTask({ id: '7', status: 'SUCCEEDED' }).terminal).toBe(true);
    expect(normalizeVideoTask({ id: '7', status: 'FAILED' }).terminal).toBe(true);
  });

  it('honours the backend terminal flag when it disagrees with the status', () => {
    expect(normalizeVideoTask({ id: '7', status: 'RUNNING', terminal: true }).terminal).toBe(true);
  });

  it('falls back to taskId and drops empty optional strings', () => {
    const task = normalizeVideoTask({ taskId: 'abc', status: 'FAILED', videoUrl: '', errorMessage: '' });

    expect(task.id).toBe('abc');
    expect(task.videoUrl).toBeUndefined();
    expect(task.errorMessage).toBeUndefined();
  });

  it('rejects a payload without an identifier', () => {
    expect(() => normalizeVideoTask({ status: 'PENDING' })).toThrow(ApiError);
    expect(() => normalizeVideoTask(null)).toThrow(ApiError);
    expect(() => normalizeVideoTask([{ id: 1 }])).toThrow(ApiError);
  });
});

describe('provider-not-configured detection', () => {
  it('recognizes the dedicated error code so the UI can explain the cause', () => {
    expect(
      isProviderNotConfigured(new ApiError(VIDEO_PROVIDER_NOT_CONFIGURED, '视频生成服务未配置')),
    ).toBe(true);
  });

  it('does not mistake other failures for a missing configuration', () => {
    expect(isProviderNotConfigured(new ApiError(48001, '视频生成供应商调用失败'))).toBe(false);
    expect(isProviderNotConfigured(new ApiError(50000, '服务内部错误'))).toBe(false);
    expect(isProviderNotConfigured(new Error('boom'))).toBe(false);
  });
});
