package com.scott.payment.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictDataDO
 * @date : 2026-07-20 00:00
 * @email : scott_x@163.com
 * @description : 商户系统只读字典数据实体，位于 service-merchant 数据实体层，仅用于读取平台公共数据字典供商户页面展示和筛选。
 * @status : create
 */
@Data
@TableName("sys_dict_data")
public class SysDictDataDO {

    /**
     * 字典项主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 字典类型编码，例如 transaction_type、transaction_status。
     */
    private String dictType;

    /**
     * 当前语言环境下的展示标签。
     */
    private String dictLabel;

    /**
     * 业务实际使用的字典值。
     */
    private String dictValue;

    /**
     * 父级字典值，可为空。
     */
    private String parentValue;

    /**
     * 语言区域，例如 zh-CN、en-US。
     */
    private String locale;

    /**
     * 展示排序，值越小越靠前。
     */
    private Integer dictSort;

    /**
     * 前端标签样式，允许为空。
     */
    private String listClass;

    /**
     * 扩展 JSON，商户端只透传不解析敏感内容。
     */
    private String extraJson;

    /**
     * 是否默认项：0 否，1 是。
     */
    private Integer isDefault;

    /**
     * 状态：0 停用，1 启用。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 删除标识：0 未删除，大于 0 表示已删除。
     */
    private Long deleted;
}
