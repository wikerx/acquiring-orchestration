package com.scott.payment.component.db.mcc.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SharedMccLevel1DO
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 公共组件数据层的 MCC 一级分类只读实体，仅用于构建跨系统共享的启用 MCC 选项快照
 * @status : create
 */
@Data
@TableName("base_mcc_level1")
public class SharedMccLevel1DO {

    /** 数据库主键，不允许为空。 */
    @TableId
    private Long id;

    /** 一级分类编码，不允许为空。 */
    private String level1Code;

    /** 中文展示名称，不允许为空。 */
    @TableField("level1_name_cn")
    private String nameCn;

    /** 英文展示名称，允许为空。 */
    @TableField("level1_name_en")
    private String nameEn;

    /** 同级展示顺序，不允许为空。 */
    private Integer sortNo;

    /** 启用状态，1 表示启用，0 表示停用，不允许为空。 */
    private Integer status;

    /** 逻辑删除标识，0 表示未删除，不允许为空。 */
    private Long deleted;
}
