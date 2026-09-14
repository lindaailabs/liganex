import { memo } from 'react';
import { Button, Input, InputNumber, Select, Space, Tag, Typography } from 'antd';
import { Handle, Position, type NodeProps, type Node } from '@xyflow/react';
import type { CanvasNodeData, CanvasNodeKind } from './graph';

/** 节点 data 除图数据外还携带回调；xyflow 约定回调随 data 下发。 */
export type CanvasFlowNodeData = CanvasNodeData & {
  kind: CanvasNodeKind;
  submitting?: boolean;
  onDataChange?: (nodeId: string, patch: CanvasNodeData) => void;
  onSubmit?: (nodeId: string) => void;
};

export type CanvasFlowNode = Node<CanvasFlowNodeData, CanvasNodeKind>;

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  SUCCEEDED: 'success',
  FAILED: 'error',
};

function StatusTag({ status }: { status?: string }) {
  if (!status) return null;
  return <Tag color={STATUS_COLORS[status] ?? 'default'}>{status}</Tag>;
}

/** 提示词节点：只提供文本，生成参数由下游生成节点推导。 */
export const PromptNode = memo(({ id, data }: NodeProps<CanvasFlowNode>) => (
  <div className="canvas-node canvas-node-prompt">
    <div className="canvas-node-title">提示词</div>
    <Input.TextArea
      className="nodrag"
      rows={3}
      placeholder="描述想要的画面，例如：一只猫在草地上奔跑，电影感光线"
      value={data.text ?? ''}
      onChange={(event) => data.onDataChange?.(id, { text: event.target.value })}
    />
    {/* 提示词没有输入口，只向下游输出 */}
    <Handle type="source" position={Position.Right} />
  </div>
));
PromptNode.displayName = 'PromptNode';

/** 参考图节点：为图生视频提供图片地址。 */
export const ImageNode = memo(({ id, data }: NodeProps<CanvasFlowNode>) => (
  <div className="canvas-node canvas-node-image">
    <div className="canvas-node-title">参考图</div>
    <Input
      className="nodrag"
      placeholder="图片地址（图生视频）"
      value={data.imageUrl ?? ''}
      onChange={(event) => data.onDataChange?.(id, { imageUrl: event.target.value })}
    />
    <Handle type="source" position={Position.Right} />
  </div>
));
ImageNode.displayName = 'ImageNode';

/** 生成节点：承接上游连线推导出的参数，并触发提交。 */
export const VideoNode = memo(({ id, data }: NodeProps<CanvasFlowNode>) => (
  <div className="canvas-node canvas-node-video">
    {/* 参数来自上游连线，故只需要输入口 */}
    <Handle type="target" position={Position.Left} />
    <div className="canvas-node-title">
      视频生成 <StatusTag status={data.status} />
    </div>
    <Space direction="vertical" size={6} style={{ width: '100%' }}>
      <Input
        className="nodrag"
        placeholder="模型（留空用服务端默认）"
        value={data.model ?? ''}
        onChange={(event) => data.onDataChange?.(id, { model: event.target.value })}
      />
      <Space size={6}>
        <InputNumber
          className="nodrag"
          min={1}
          max={60}
          placeholder="秒数"
          value={data.durationSeconds}
          onChange={(value) =>
            data.onDataChange?.(id, { durationSeconds: value ?? undefined })
          }
        />
        <Select
          className="nodrag"
          style={{ width: 130 }}
          placeholder="分辨率"
          allowClear
          value={data.size}
          onChange={(value) => data.onDataChange?.(id, { size: value })}
          options={[
            { value: '720x1280', label: '720x1280' },
            { value: '1280x720', label: '1280x720' },
            { value: '1024x1024', label: '1024x1024' },
          ]}
        />
      </Space>
      <Button
        className="nodrag"
        type="primary"
        block
        loading={data.submitting}
        onClick={() => data.onSubmit?.(id)}
      >
        生成
      </Button>
      {data.errorMessage ? (
        <Typography.Text type="danger" className="canvas-node-message">
          {data.errorMessage}
        </Typography.Text>
      ) : null}
    </Space>
    <Handle type="source" position={Position.Right} />
  </div>
));
VideoNode.displayName = 'VideoNode';

/** 结果预览节点：展示任务状态与成片。 */
export const PreviewNode = memo(({ id, data }: NodeProps<CanvasFlowNode>) => (
  <div className="canvas-node canvas-node-preview">
    <Handle type="target" position={Position.Left} />
    <div className="canvas-node-title">
      结果预览 <StatusTag status={data.status} />
    </div>
    {data.videoUrl ? (
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <video className="canvas-preview-video" controls src={data.videoUrl} />
        <Typography.Link href={data.videoUrl} target="_blank" rel="noreferrer">
          在新窗口打开
        </Typography.Link>
      </Space>
    ) : (
      <Typography.Text type="secondary" className="canvas-node-message">
        {data.errorMessage ?? '把生成节点连到此处，结果会显示在这里'}
      </Typography.Text>
    )}
    <div className="canvas-node-footnote" data-node-id={id} />
  </div>
));
PreviewNode.displayName = 'PreviewNode';

/** 必须在组件外定义，否则每次渲染都会重建节点类型并导致整图重挂载。 */
export const nodeTypes = {
  prompt: PromptNode,
  image: ImageNode,
  video: VideoNode,
  preview: PreviewNode,
};
