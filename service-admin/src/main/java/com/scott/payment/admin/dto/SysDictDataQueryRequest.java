package com.scott.payment.admin.dto;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictDataQueryRequest
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 字典数据查询请求
 * @status : create
 */
@Data
public class SysDictDataQueryRequest {

    /**
     * 字典类型编码，支持精确查询。
     */
    private String dictType;

    /**
     * 字典标签，支持右模糊查询。
     */
    private String dictLabel;

    /**
     * 字典键值，支持精确查询。
     */
    private String dictValue;

    /**
     * 父级字典值，支持精确查询。
     */
    private String parentValue;

    /**
     * 语言区域。
     */
    private String locale;

    /**
     * 状态：0停用，1启用。
     */
    private Integer status;
}
