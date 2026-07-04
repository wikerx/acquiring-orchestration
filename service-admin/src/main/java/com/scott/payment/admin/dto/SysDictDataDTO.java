package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictDataDTO
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 字典数据响应 DTO
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictDataDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Dict Data 数据传输对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysDictDataDTO {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 字典类型编码。
     */
    private String dictType;

    /**
     * 字典标签。
     */
    private String dictLabel;

    /**
     * 字典键值。
     */
    private String dictValue;

    /**
     * 父级字典值。
     */
    private String parentValue;

    /**
     * 语言区域。
     */
    private String locale;

    /**
     * 排序。
     */
    private Integer dictSort;

    /**
     * 展示样式。
     */
    private String listClass;

    /**
     * 扩展属性 JSON。
     */
    private String extraJson;

    /**
     * 是否默认：0否，1是。
     */
    private Integer isDefault;

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
