package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典类型保存请求。
 *
 * <p>用于后台字典主表的新增和更新，不包含字典项列表。</p>
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
