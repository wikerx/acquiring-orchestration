package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户岗位关联数据库实体。
 *
 * <p>维护自然人用户与岗位的多对多关系，仅用于组织岗位展示和筛选，不替代账号角色授权。</p>
 */
@Data
@TableName("sys_user_post")
public class SysUserPostDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户主体 ID，对应 sys_user.id。
     */
    private Long userId;

    /**
     * 岗位 ID，对应 sys_post.id。
     */
    private Long postId;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
