package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictDataSaveRequest
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 字典数据保存请求
 * @status : create
 */
@Data
public class SysDictDataSaveRequest {

    /**
     * 字典类型编码。
     */
    @NotBlank(message = "dictType不能为空")
    private String dictType;

    /**
     * 字典标签。
     */
    @NotBlank(message = "dictLabel不能为空")
    private String dictLabel;

    /**
     * 字典键值。
     */
    @NotBlank(message = "dictValue不能为空")
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
     * 当前操作人。
     */
    private String operator;
}
