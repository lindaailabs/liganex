import { Typography } from 'antd';
import { VideoCanvas } from '../features/video-canvas';

export default function VideoCanvasPage() {
  return (
    <div className="canvas-page">
      <Typography.Paragraph type="secondary" className="canvas-hint">
        把「提示词 / 参考图」连到「视频生成」，再连到「结果预览」，即可提交生成任务。
        生成参数由连线推导得出，无需在生成节点上重复填写提示词。
      </Typography.Paragraph>
      <VideoCanvas />
    </div>
  );
}
