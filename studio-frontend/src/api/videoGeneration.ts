import client, { ApiError } from './client';

const API_PREFIX = '/v1/video-generations';

/** 与后端统一状态机一一对应（供应商自有状态已在后端适配器内归一化）。 */
export type VideoTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

/**
 * 终态集合：前端据此停止轮询。
 *
 * 后端响应里也带 `terminal` 字段，两者取或——不依赖任一单点，避免任一侧漏改导致无限轮询。
 */
export const TERMINAL_STATUSES: ReadonlySet<VideoTaskStatus> = new Set<VideoTaskStatus>([
  'SUCCEEDED',
  'FAILED',
]);

export function isTerminalStatus(status: VideoTaskStatus): boolean {
  return TERMINAL_STATUSES.has(status);
}

/** 后端 ErrorCode.VIDEO_PROVIDER_NOT_CONFIGURED：服务未配置（HTTP 503），需给用户可行动的提示。 */
export const VIDEO_PROVIDER_NOT_CONFIGURED = 46001;

export function isProviderNotConfigured(error: unknown): boolean {
  return error instanceof ApiError && error.code === VIDEO_PROVIDER_NOT_CONFIGURED;
}

export interface VideoGenerationTask {
  id: string;
  provider: string;
  model: string;
  status: VideoTaskStatus;
  terminal: boolean;
  prompt: string;
  videoUrl?: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
}

export interface SubmitVideoGenerationRequest {
  prompt: string;
  provider?: string;
  model?: string;
  durationSeconds?: number;
  size?: string;
  imageUrl?: string;
}

type JsonRecord = Record<string, unknown>;

function record(value: unknown, label: string): JsonRecord {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new ApiError(-1, `${label}响应格式不正确`);
  }
  return value as JsonRecord;
}

function identifier(value: unknown, label: string): string {
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  throw new ApiError(-1, `${label}缺少标识`);
}

function text(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function listPayload(value: unknown, label: string): unknown[] {
  if (Array.isArray(value)) return value;
  const payload = record(value, label);
  const items = payload.items ?? payload.records ?? payload.content;
  if (Array.isArray(items)) return items;
  throw new ApiError(-1, `${label}列表响应格式不正确`);
}

/**
 * 归一化状态字面量。
 *
 * 后端已保证只发四种统一状态，这里额外容忍少量别名；**未知值按进行中处理**而不是失败——
 * 让用户看到一个假的「生成失败」比多轮询几次更糟，轮询本身有次数上限兜底。
 */
export function normalizeStatus(value: unknown): VideoTaskStatus {
  const status = text(value).trim().toUpperCase();
  if (status === 'PENDING' || status === 'RUNNING' || status === 'SUCCEEDED' || status === 'FAILED') {
    return status;
  }
  if (status === 'SUCCESS' || status === 'COMPLETED') return 'SUCCEEDED';
  if (status === 'ERROR' || status === 'CANCELLED' || status === 'CANCELED') return 'FAILED';
  return 'RUNNING';
}

export function normalizeVideoTask(value: unknown): VideoGenerationTask {
  const item = record(value, '视频生成任务');
  const status = normalizeStatus(item.status);
  return {
    id: identifier(item.id ?? item.taskId, '视频生成任务'),
    provider: text(item.provider),
    model: text(item.model),
    status,
    // 状态与后端 terminal 字段取或，任一侧判定为终态即停止轮询
    terminal: isTerminalStatus(status) || item.terminal === true,
    prompt: text(item.prompt),
    videoUrl: text(item.videoUrl) || undefined,
    errorMessage: text(item.errorMessage) || undefined,
    createdAt: text(item.createdAt),
    updatedAt: text(item.updatedAt),
    completedAt: text(item.completedAt) || undefined,
  };
}

/** 省略空字段，避免向后端发送 `""` 这类需要额外分支处理的取值。 */
function stripEmpty(request: SubmitVideoGenerationRequest): JsonRecord {
  const payload: JsonRecord = { prompt: request.prompt };
  if (request.provider) payload.provider = request.provider;
  if (request.model) payload.model = request.model;
  if (typeof request.durationSeconds === 'number' && Number.isFinite(request.durationSeconds)) {
    payload.durationSeconds = request.durationSeconds;
  }
  if (request.size) payload.size = request.size;
  if (request.imageUrl) payload.imageUrl = request.imageUrl;
  return payload;
}

export async function submitVideoGeneration(
  request: SubmitVideoGenerationRequest,
): Promise<VideoGenerationTask> {
  return normalizeVideoTask(await client.post<unknown>(API_PREFIX, stripEmpty(request)));
}

export async function getVideoGeneration(taskId: string): Promise<VideoGenerationTask> {
  return normalizeVideoTask(await client.get<unknown>(`${API_PREFIX}/${taskId}`));
}

export async function listVideoGenerations(limit = 20): Promise<VideoGenerationTask[]> {
  const response = await client.get<unknown>(API_PREFIX, { params: { limit } });
  return listPayload(response, '视频生成任务').map(normalizeVideoTask);
}
