package com.scott.payment.component.db.mcc.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MccOptionSnapshot
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 跨系统共享的启用 MCC 三级级联选项快照
 * @status : create
 */
@Data
public class MccOptionSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 父级为内部级联值，叶子节点为四位 MCC 编码。 */
    private String value;

    /** 兼容旧调用方的完整展示文本。 */
    private String label;

    /** 中文名称。 */
    private String nameCn;

    /** 英文名称。 */
    private String nameEn;

    /** 下级分类或 MCC 编码。 */
    private List<MccOptionSnapshot> children = new ArrayList<>();
}
