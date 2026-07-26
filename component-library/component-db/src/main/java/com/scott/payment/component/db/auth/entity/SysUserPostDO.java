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
 * @description : SysUserPostDO 数据库实体，用于映射持久化表字段、审计字段和业务状态，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
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
