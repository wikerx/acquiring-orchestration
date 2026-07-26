package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_post")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserPostDO
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sys User Post DO 持久化模型，位于 公共组件库，映射数据库记录字段，承载主键、业务标识、状态、时间和审计信息。
 * @status : create
 */
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
