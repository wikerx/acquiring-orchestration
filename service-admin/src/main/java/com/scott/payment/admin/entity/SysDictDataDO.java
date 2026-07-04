package com.scott.payment.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictDataDO
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 字典数据数据库实体
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictDataDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Dict Data 数据库实体，位于 service-admin 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("sys_dict_data")
public class SysDictDataDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 字典类型编码，对应 sys_dict_type.dict_type。
     */
    private String dictType;

    /**
     * 字典标签，前端展示值。
     */
    private String dictLabel;

    /**
     * 字典键值，业务实际值。
     */
    private String dictValue;

    /**
     * 父级字典值，用于层级字典。
     */
    private String parentValue;

    /**
     * 语言区域，如 zh-CN、en-US。
     */
    private String locale;

    /**
     * 排序，值越小越靠前。
     */
    private Integer dictSort;

    /**
     * 展示样式：default、primary、success、warning、danger。
     */
    private String listClass;

    /**
     * 扩展属性 JSON，如图标、颜色、渠道映射值。
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
     * 创建人。
     */
    private String createdBy;

    /**
     * 更新人。
     */
    private String updatedBy;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 删除标识：0未删除，大于0为删除记录ID。
     */
    private Long deleted;
}
