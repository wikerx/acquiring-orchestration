package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysNoticeDO
 * @date : 2026-06-12 17:37
 * @email : scott_x@163.com
 * @description : 通知公告数据库实体，对应 sys_notice 表
 * @status : create
 */
@Data
@TableName("sys_notice")
public class SysNoticeDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 通知标题。
     */
    private String noticeTitle;

    /**
     * 通知类型：1 通知，2 公告。
     */
    private String noticeType;

    /**
     * 通知内容（支持长文本）。
     */
    private String noticeContent;

    /**
     * 状态：0 停用，1 启用。
     */
    private Integer status;

    /**
     * 创建人。
     */
    private String createBy;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 删除标识：0 未删除，大于 0 为删除记录 ID。
     */
    private Long deleted;
}
