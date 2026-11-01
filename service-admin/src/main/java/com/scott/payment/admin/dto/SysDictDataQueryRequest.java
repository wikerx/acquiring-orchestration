package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDictDataQueryRequest extends PageRequest {

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
