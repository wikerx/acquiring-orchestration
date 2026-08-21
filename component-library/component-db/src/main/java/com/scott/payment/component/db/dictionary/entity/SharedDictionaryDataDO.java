package com.scott.payment.component.db.dictionary.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SharedDictionaryDataDO
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 公共数据字典快照查询实体，仅映射两端下拉所需字段
 * @status : create
 */
@Data
@TableName("sys_dict_data")
public class SharedDictionaryDataDO {

    /** 字典项主键。 */
    @TableId
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

    /** 逻辑删除标识。 */
    private Long deleted;
}
