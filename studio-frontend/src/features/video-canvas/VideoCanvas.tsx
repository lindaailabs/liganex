import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Space } from 'antd';
import {
  Background,
  Controls,
  MiniMap,
  Panel,
  ReactFlow,
  addEdge,
  useEdgesState,
  useNodesState,
  type Connection,
  type Edge,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { extractError } from '../../api/client';
import {
  isProviderNotConfigured,
  submitVideoGeneration,
  type VideoGenerationTask,
} from '../../api/videoGeneration';
import { nodeTypes, type CanvasFlowNode } from './CanvasNodes';
import {
  deriveRequest,
  previewTargetFor,
  type CanvasGraph,
  type CanvasNodeData,
  type CanvasNodeKind,
} from './graph';
import { useVideoGenerationPolling } from './useVideoGenerationPolling';

const POLL_INTERVAL_MS = 3000;

/** 预置一条可用链路，打开页面即可直接试验；新增节点由工具条完成。 */
const initialNodes: CanvasFlowNode[] = [
  {
    id: 'prompt-1',
    type: 'prompt',
    position: { x: 40, y: 180 },
    data: { kind: 'prompt', text: '' },
  },
  {
    id: 'video-1',
    type: 'video',
    position: { x: 420, y: 150 },
    data: { kind: 'video', durationSeconds: 4, size: '720x1280' },
  },
  {
    id: 'preview-1',
    type: 'preview',
    position: { x: 820, y: 190 },
    data: { kind: 'preview' },
  },
];

const initialEdges: Edge[] = [
  { id: 'prompt-1->video-1', source: 'prompt-1', target: 'video-1' },
  { id: 'video-1->preview-1', source: 'video-1', target: 'preview-1' },
];

const PALETTE: Array<{ kind: CanvasNodeKind; label: string }> = [
  { kind: 'prompt', label: '提示词' },
  { kind: 'image', label: '参考图' },
  { kind: 'video', label: '视频生成' },
  { kind: 'preview', label: '结果预览' },
];

/** 幂等判据：避免把相同结果重复写回节点，否则会在「写入 → 图变化 → 再写入」间打转。 */
function sameData(current: CanvasNodeData, next: CanvasNodeData): boolean {
  return (
    current.taskId === next.taskId &&
    current.status === next.status &&
    current.videoUrl === next.videoUrl &&
    current.errorMessage === next.errorMessage
  );
}

export default function VideoCanvas() {
  const [nodes, setNodes, onNodesChange] = useNodesState<CanvasFlowNode>(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>(initialEdges);
  const [active, setActive] = useState<{ nodeId: string; taskId: string } | null>(null);
  const [submittingNodeId, setSubmittingNodeId] = useState<string | null>(null);
  const [providerMissing, setProviderMissing] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const sequence = useRef(0);

  const graph = useMemo<CanvasGraph>(
    () => ({
      nodes: nodes.map((node) => ({
        id: node.id,
        kind: node.type as CanvasNodeKind,
        data: node.data,
      })),
      edges: edges.map((edge) => ({ id: edge.id, source: edge.source, target: edge.target })),
    }),
    [nodes, edges],
  );

  // 事件回调只读最新图，但不把 graph 放进依赖：否则每次节点变化都会重建回调
  const graphRef = useRef(graph);
  useEffect(() => {
    graphRef.current = graph;
  });

  const { task, error: pollError } = useVideoGenerationPolling(active?.taskId ?? null, {
    intervalMs: POLL_INTERVAL_MS,
  });

  const updateNodeData = useCallback(
    (nodeId: string, patch: CanvasNodeData) => {
      setNodes((current) =>
        current.map((node) =>
          node.id === nodeId ? { ...node, data: { ...node.data, ...patch } } : node,
        ),
      );
    },
    [setNodes],
  );

  const applyTask = useCallback(
    (videoNodeId: string, value: VideoGenerationTask) => {
      const previewId = previewTargetFor(graphRef.current, videoNodeId)?.id;
      const patch: CanvasNodeData = {
        taskId: value.id,
        status: value.status,
        videoUrl: value.videoUrl,
        errorMessage: value.status === 'FAILED' ? value.errorMessage : undefined,
      };
      setNodes((current) =>
        current.map((node) => {
          if (node.id !== videoNodeId && node.id !== previewId) return node;
          return sameData(node.data, patch) ? node : { ...node, data: { ...node.data, ...patch } };
        }),
      );
    },
    [setNodes],
  );

  // 轮询结果回填到生成节点与其下游预览节点
  useEffect(() => {
    if (task && active) applyTask(active.nodeId, task);
  }, [task, active, applyTask]);

  const submit = useCallback(
    async (videoNodeId: string) => {
      setNotice(null);

      // 先在本地推导；缺上游提示词时直接给出可读原因，不发无意义的请求
      const derived = deriveRequest(graphRef.current, videoNodeId);
      if (!derived.ok) {
        updateNodeData(videoNodeId, { errorMessage: derived.reason });
        setNotice(derived.reason);
        return;
      }

      setSubmittingNodeId(videoNodeId);
      updateNodeData(videoNodeId, { errorMessage: undefined, status: undefined });
      try {
        const created = await submitVideoGeneration(derived.request);
        setProviderMissing(false);
        applyTask(videoNodeId, created);
        setActive({ nodeId: videoNodeId, taskId: created.id });
      } catch (error) {
        if (isProviderNotConfigured(error)) {
          // 明确告知是高优先级配置缺失，而不是让用户看到泛化的「请求失败」
          setProviderMissing(true);
          updateNodeData(videoNodeId, { errorMessage: '视频生成服务未配置' });
        } else {
          const reason = extractError(error);
          setNotice(reason);
          updateNodeData(videoNodeId, { errorMessage: reason });
        }
      } finally {
        setSubmittingNodeId(null);
      }
    },
    [applyTask, updateNodeData],
  );

  const addNode = useCallback(
    (kind: CanvasNodeKind) => {
      sequence.current += 1;
      const index = sequence.current;
      setNodes((current) => [
        ...current,
        {
          id: `${kind}-new-${index}`,
          type: kind,
          position: { x: 80 + (index % 4) * 60, y: 420 + (index % 3) * 60 },
          data: { kind },
        },
      ]);
    },
    [setNodes],
  );

  const onConnect = useCallback(
    (connection: Connection) => {
      setEdges((current) => addEdge(connection, current));
    },
    [setEdges],
  );

  const displayNodes = useMemo(
    () =>
      nodes.map((node) => ({
        ...node,
        data: {
          ...node.data,
          submitting: submittingNodeId === node.id,
          onDataChange: updateNodeData,
          onSubmit: submit,
        },
      })),
    [nodes, submittingNodeId, updateNodeData, submit],
  );

  return (
    <div className="canvas-shell">
      {providerMissing ? (
        <Alert
          className="canvas-alert"
          type="warning"
          showIcon
          message="视频生成服务未配置"
          description="服务端未配置视频生成供应商凭证，本次没有发起任何生成请求（不会产生费用）。请在服务端注入 LIGANEX_VIDEO_OPENAI_BASE_URL 与 LIGANEX_VIDEO_OPENAI_API_KEY 后重试。"
        />
      ) : null}
      {notice ? (
        <Alert
          className="canvas-alert"
          type="error"
          showIcon
          closable
          message={notice}
          onClose={() => setNotice(null)}
        />
      ) : null}
      {pollError ? (
        <Alert className="canvas-alert" type="error" showIcon message={`状态查询失败：${pollError}`} />
      ) : null}

      <div className="canvas-flow">
        <ReactFlow
          nodes={displayNodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          fitView
          proOptions={{ hideAttribution: true }}
        >
          <Background />
          <Controls />
          <MiniMap />
          <Panel position="top-left">
            <Space size={6} wrap>
              {PALETTE.map(({ kind, label }) => (
                <Button key={kind} size="small" onClick={() => addNode(kind)}>
                  添加{label}
                </Button>
              ))}
            </Space>
          </Panel>
        </ReactFlow>
      </div>
    </div>
  );
}
