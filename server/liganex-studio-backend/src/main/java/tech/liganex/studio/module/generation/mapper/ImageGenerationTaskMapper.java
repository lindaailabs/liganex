package tech.liganex.studio.module.generation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tech.liganex.studio.module.generation.entity.ImageGenerationTask;

import java.util.List;

/**
 * 图片生成任务 Mapper。
 *
 * <p>所有按 id 的读取都带 {@code owner_user_id} 条件：owner 隔离在 SQL 层完成，
 * 越权访问与「不存在」返回同一结果（上层统一转 404），不泄露资源是否存在。
 */
public interface ImageGenerationTaskMapper extends BaseMapper<ImageGenerationTask> {

    @Select("""
            SELECT * FROM image_generation_task
            WHERE owner_user_id = #{ownerUserId} AND id = #{id}
            """)
    ImageGenerationTask selectOwnedById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("id") Long id);

    @Select("""
            SELECT * FROM image_generation_task
            WHERE owner_user_id = #{ownerUserId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<ImageGenerationTask> selectAllOwned(
            @Param("ownerUserId") Long ownerUserId,
            @Param("limit") int limit);
}
