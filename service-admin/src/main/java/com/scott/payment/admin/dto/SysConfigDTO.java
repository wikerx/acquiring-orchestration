package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigDTO
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 系统参数配置响应 DTO
 * @status : create
 */
@Data
public class SysConfigDTO {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 参数名称。
     */
    private String configName;

    /**
     * 参数键名。
     */
    private String configKey;

    /**
     * 参数键值。
     */
    private String configValue;

    /**
     * 值类型：1字符串，2数字，3布尔，4JSON。
     */
    private Integer valueType;

    /**
     * 配置分组。
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
     * 是否加密存储：0否，1是。
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
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
