package com.scott.payment.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigDO
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 系统参数配置数据库实体
 * @status : create
 */
@Data
@TableName("sys_config")
public class SysConfigDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 参数名称，用于后台列表展示。
     */
    private String configName;

    /**
     * 参数键名，全局唯一。
     */
    private String configKey;

    /**
     * 参数键值，支持普通文本或 JSON 字符串。
     */
    private String configValue;

    /**
     * 值类型：1字符串，2数字，3布尔，4JSON。
     */
    private Integer valueType;

    /**
     * 配置分组，如 system、merchant、risk、settlement。
     */
    private String configGroup;

    /**
     * 是否系统内置：0否，1是。
     */
    private Integer systemBuiltin;

    /**
     * 是否前端可见：0否，1是。
     */
    private Integer visible;

    /**
     * 是否加密存储：0否，1是；密钥类配置不建议放本表。
     */
    private Integer encrypted;

    /**
     * 状态：0停用，1启用。
     */
    private Integer status;

    /**
     * 备注说明。
     */
    private String remark;

    /**
     * 创建人。
     */
    private String createdBy;

    /**
     * 更新人。
     */
    private String updatedBy;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 删除标识：0未删除，大于0为删除记录ID。
     */
    private Long deleted;
}
