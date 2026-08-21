package com.scott.payment.component.db.dictionary.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DictionaryOptionSnapshot
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 跨系统共享的启用数据字典下拉快照，不包含管理审计字段
 * @status : create
 */
@Data
public class DictionaryOptionSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字典项主键。 */
    private Long id;

    /** 字典类型编码。 */
    private String dictType;

    /** 展示标签。 */
    private String dictLabel;

    /** 业务键值。 */
    private String dictValue;

    /** 父级字典值。 */
    private String parentValue;

    /** 语言区域。 */
    private String locale;

    /** 展示排序。 */
    private Integer dictSort;

    /** 前端标签样式。 */
    private String listClass;

    /** 扩展属性 JSON。 */
    private String extraJson;

    /** 是否默认项。 */
    private Integer isDefault;

    /** 启用状态。 */
    private Integer status;
}
