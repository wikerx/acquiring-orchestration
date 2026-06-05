package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictTypeDTO
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 字典类型响应 DTO
 * @status : create
 */
@Data
public class SysDictTypeDTO {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 字典名称。
     */
    private String dictName;

    /**
     * 字典类型编码。
     */
    private String dictType;

    /**
     * 业务域。
     */
    private String bizDomain;

    /**
     * 是否系统内置：0否，1是。
     */
    private Integer systemBuiltin;

    /**
     * 是否允许编辑：0否，1是。
     */
    private Integer editable;

    /**
     * 状态：0停用，1启用。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
