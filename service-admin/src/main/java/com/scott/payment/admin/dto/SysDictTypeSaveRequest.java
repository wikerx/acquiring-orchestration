package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictTypeSaveRequest
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 字典类型保存请求
 * @status : create
 */
@Data
public class SysDictTypeSaveRequest {

    /**
     * 字典名称。
     */
    @NotBlank(message = "dictName不能为空")
    private String dictName;

    /**
     * 字典类型编码。
     */
    @NotBlank(message = "dictType不能为空")
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
     * 当前操作人。
     */
    private String operator;
}
