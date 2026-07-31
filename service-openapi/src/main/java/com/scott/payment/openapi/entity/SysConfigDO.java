package com.scott.payment.openapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数配置数据库实体，service-openapi 仅按白名单 key 读取平台运行配置。
 */
@Data
@TableName("sys_config")
public class SysConfigDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 参数名称，用于后台展示。
     */
    private String configName;

    /**
     * 全局唯一的参数键名。
     */
    private String configKey;

    /**
     * 参数值，支持普通文本或 JSON 字符串。
     */
    private String configValue;

    /**
     * 值类型：1 字符串，2 数字，3 布尔，4 JSON。
     */
    private Integer valueType;

    /**
     * 配置所属业务分组。
     */
    private String configGroup;

    /**
     * 是否为系统内置配置：0 否，1 是。
     */
    private Integer systemBuiltin;

    /**
     * 是否允许前端展示：0 否，1 是。
     */
    private Integer visible;

    /**
     * 是否加密存储：0 否，1 是；密钥类配置不应写入本表。
     */
    private Integer encrypted;

    /**
     * 配置状态：0 停用，1 启用。
     */
    private Integer status;

    /**
     * 参数用途或维护说明。
     */
    private String remark;

    /**
     * 创建人标识。
     */
    private String createdBy;

    /**
     * 最近更新人标识。
     */
    private String updatedBy;

    /**
     * 数据库记录创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 数据库记录最近更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标识：0 表示未删除，大于 0 表示已删除记录。
     */
    private Long deleted;
}
