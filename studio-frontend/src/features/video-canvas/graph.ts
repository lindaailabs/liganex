import type { SubmitVideoGenerationRequest } from '../../api/videoGeneration';

/**
 * 画布图模型与「连线 → 参数推导」纯逻辑。
 *
 * 刻意与 React / xyflow 解耦：画布渲染在 jsdom 下不稳定，因此把可验证的推导规则全部放在这里，
 * 组件只负责把结果画出来。
 */

export type CanvasNodeKind = 'prompt' | 'image' | 'video' | 'preview';

/**
 * 用 type 而非 interface：xyflow 的节点 data 需要满足 `Record<string, unknown>`，
 * 而只有类型别名会获得隐式索引签名，interface 不会。
 */
export type CanvasNodeData = {
  /** prompt 节点的文本内容。 */
  text?: string;
  /** image 节点的参考图地址（图生视频）。 */
  imageUrl?: string;
  /** video 节点的模型覆盖值；留空则用后端默认。 */
  model?: string;
  durationSeconds?: number;
  size?: string;
  /** video / preview 节点上的任务标识。 */
  taskId?: string;
  /** 任务状态字面量，用于节点上展示进度。 */
  status?: string;
  videoUrl?: string;
  errorMessage?: string;
};

export interface CanvasNode {
  id: string;
  kind: CanvasNodeKind;
  data: CanvasNodeData;
}

export interface CanvasEdge {
  id: string;
  source: string;
  target: string;
}

export interface CanvasGraph {
  nodes: CanvasNode[];
  edges: CanvasEdge[];
}

export type DerivationResult =
  | { ok: true; request: SubmitVideoGenerationRequest }
  | { ok: false; reason: string };

/** 直接指向 target 的上游节点，按节点在数组中的顺序返回（保证推导结果稳定、可测试）。 */
export function upstreamNodes(graph: CanvasGraph, targetId: string): CanvasNode[] {
  const sources = new Set(
    graph.edges.filter((edge) => edge.target === targetId).map((edge) => edge.source),
  );
  return graph.nodes.filter((node) => sources.has(node.id));
}

/** 直接从 source 出发的下游节点。 */
export function downstreamNodes(graph: CanvasGraph, sourceId: string): CanvasNode[] {
  const targets = new Set(
    graph.edges.filter((edge) => edge.source === sourceId).map((edge) => edge.target),
  );
  return graph.nodes.filter((node) => targets.has(node.id));
}

function nonBlank(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

/**
 * 由上游连线推导出提交给后端的生成请求。
 *
 * 规则：
 * - 上游 prompt 节点内容按节点顺序拼接为 prompt，生成节点自身不重复录入提示词；
 * - 上游 image 节点提供参考图（图生视频）；
 * - 缺少上游提示词时返回可读原因，调用方据此阻止提交，不发无意义的请求。
 */
export function deriveRequest(graph: CanvasGraph, videoNodeId: string): DerivationResult {
  const videoNode = graph.nodes.find((node) => node.id === videoNodeId);
  if (!videoNode) {
    return { ok: false, reason: '生成节点不存在' };
  }
  if (videoNode.kind !== 'video') {
    return { ok: false, reason: '该节点不是视频生成节点' };
  }

  const upstream = upstreamNodes(graph, videoNodeId);

  const prompt = upstream
    .filter((node) => node.kind === 'prompt')
    .map((node) => nonBlank(node.data.text))
    .filter((value): value is string => Boolean(value))
    .join('\n');

  if (!prompt) {
    return { ok: false, reason: '请先连接提示词节点并填写内容' };
  }

  const imageUrl = upstream
    .filter((node) => node.kind === 'image')
    .map((node) => nonBlank(node.data.imageUrl))
    .find((value): value is string => Boolean(value));

  const durationSeconds =
    typeof videoNode.data.durationSeconds === 'number' &&
    Number.isFinite(videoNode.data.durationSeconds) &&
    videoNode.data.durationSeconds > 0
      ? videoNode.data.durationSeconds
      : undefined;

  return {
    ok: true,
    request: {
      prompt,
      imageUrl,
      model: nonBlank(videoNode.data.model),
      size: nonBlank(videoNode.data.size),
      durationSeconds,
    },
  };
}

/** 视频生成节点的首个下游预览节点，用于把任务结果回填到画布。 */
export function previewTargetFor(graph: CanvasGraph, videoNodeId: string): CanvasNode | undefined {
  return downstreamNodes(graph, videoNodeId).find((node) => node.kind === 'preview');
}
