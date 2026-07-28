package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictTypeQueryRequest
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 字典类型查询请求
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDictTypeQueryRequest extends PageRequest {

    /**
     * 字典名称，支持右模糊查询。
     */
    private String dictName;

    /**
     * 字典类型编码，支持精确查询。
     */
    private String dictType;

    /**
     * 业务域，支持精确查询。
     */
    private String bizDomain;

    /**
     * 状态：0停用，1启用。
     */
    private Integer status;
}
