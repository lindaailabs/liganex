import { describe, expect, it } from 'vitest';
import { deriveRequest, downstreamNodes, previewTargetFor, upstreamNodes } from './graph';
import type { CanvasGraph } from './graph';

function wiredGraph(): CanvasGraph {
  return {
    nodes: [
      { id: 'p1', kind: 'prompt', data: { text: '一只猫在草地上奔跑' } },
      { id: 'p2', kind: 'prompt', data: { text: '  电影感光线  ' } },
      { id: 'blank', kind: 'prompt', data: { text: '   ' } },
      { id: 'img', kind: 'image', data: { imageUrl: 'https://cdn.example/cat.png' } },
      { id: 'v1', kind: 'video', data: { durationSeconds: 4, size: '720x1280', model: 'sora-2' } },
      { id: 'out', kind: 'preview', data: {} },
      { id: 'orphan', kind: 'prompt', data: { text: '没有被连线' } },
    ],
    edges: [
      { id: 'e1', source: 'p1', target: 'v1' },
      { id: 'e2', source: 'p2', target: 'v1' },
      { id: 'e3', source: 'blank', target: 'v1' },
      { id: 'e4', source: 'img', target: 'v1' },
      { id: 'e5', source: 'v1', target: 'out' },
    ],
  };
}

describe('canvas graph traversal', () => {
  it('lists upstream nodes following the edges', () => {
    const ids = upstreamNodes(wiredGraph(), 'v1').map((node) => node.id);
    expect(ids).toEqual(['p1', 'p2', 'blank', 'img']);
  });

  it('lists downstream nodes following the edges', () => {
    expect(downstreamNodes(wiredGraph(), 'v1').map((node) => node.id)).toEqual(['out']);
  });

  it('finds the preview node wired downstream of the generator', () => {
    expect(previewTargetFor(wiredGraph(), 'v1')?.id).toBe('out');
  });

  it('returns no preview target when nothing is wired', () => {
    const graph = wiredGraph();
    graph.edges = graph.edges.filter((edge) => edge.id !== 'e5');
    expect(previewTargetFor(graph, 'v1')).toBeUndefined();
  });
});

describe('deriveRequest', () => {
  it('derives the prompt from upstream prompt nodes in node order', () => {
    const result = deriveRequest(wiredGraph(), 'v1');

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    // 生成节点自身不录入提示词；空白节点被丢弃，其余按画布顺序拼接
    expect(result.request.prompt).toBe('一只猫在草地上奔跑\n电影感光线');
  });

  it('carries the connected image reference for image-to-video', () => {
    const result = deriveRequest(wiredGraph(), 'v1');

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.request.imageUrl).toBe('https://cdn.example/cat.png');
  });

  it('carries generator node parameters', () => {
    const result = deriveRequest(wiredGraph(), 'v1');

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.request.model).toBe('sora-2');
    expect(result.request.size).toBe('720x1280');
    expect(result.request.durationSeconds).toBe(4);
  });

  it('ignores prompt nodes that are not wired in', () => {
    const result = deriveRequest(wiredGraph(), 'v1');

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.request.prompt).not.toContain('没有被连线');
  });

  it('refuses to build a request when no prompt is connected', () => {
    const graph = wiredGraph();
    graph.edges = graph.edges.filter((edge) => !['e1', 'e2', 'e3'].includes(edge.id));

    const result = deriveRequest(graph, 'v1');

    expect(result).toEqual({ ok: false, reason: '请先连接提示词节点并填写内容' });
  });

  it('refuses to build a request when connected prompts are blank', () => {
    const graph = wiredGraph();
    graph.edges = graph.edges.filter((edge) => !['e1', 'e2'].includes(edge.id));

    expect(deriveRequest(graph, 'v1')).toEqual({
      ok: false,
      reason: '请先连接提示词节点并填写内容',
    });
  });

  it('rejects a node that is not a generator', () => {
    expect(deriveRequest(wiredGraph(), 'p1')).toEqual({
      ok: false,
      reason: '该节点不是视频生成节点',
    });
  });

  it('rejects an unknown node id', () => {
    expect(deriveRequest(wiredGraph(), 'missing')).toEqual({
      ok: false,
      reason: '生成节点不存在',
    });
  });

  it('drops non-positive durations instead of sending them', () => {
    const graph = wiredGraph();
    graph.nodes = graph.nodes.map((node) =>
      node.id === 'v1' ? { ...node, data: { durationSeconds: 0 } } : node,
    );

    const result = deriveRequest(graph, 'v1');

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.request.durationSeconds).toBeUndefined();
  });

  it('omits optional fields when the generator node leaves them empty', () => {
    const graph = wiredGraph();
    graph.nodes = graph.nodes.map((node) => (node.id === 'v1' ? { ...node, data: {} } : node));
    graph.edges = graph.edges.filter((edge) => edge.source !== 'img');

    const result = deriveRequest(graph, 'v1');

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.request.model).toBeUndefined();
    expect(result.request.size).toBeUndefined();
    expect(result.request.imageUrl).toBeUndefined();
  });
});
