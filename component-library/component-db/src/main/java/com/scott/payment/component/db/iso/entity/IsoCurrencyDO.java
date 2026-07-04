package com.scott.payment.component.db.iso.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCurrencyDO
 * @date : 2026-06-03 14:24
 * @email : scott_x@163.com
 * @description : ISO 4217 币种基础字典数据库实体
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCurrencyDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Iso Currency 数据库实体，位于 component-library/component-db 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("base_iso_currency")
public class IsoCurrencyDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * ISO 4217 三位字母币种代码，例如 USD、CNY。
     */
    private String alpha3Code;

    /**
     * ISO 4217 三位数字币种代码，例如 840、156。
     */
    private String numericCode;

    /**
     * 币种英文名称。
     */
    private String englishName;

    /**
     * 币种中文名称。
     */
    private String chineseName;

    /**
     * 币种符号或图标，例如 $、¥、€。
     */
    private String currencySymbol;

    /**
     * 默认辅币位，小于 0 表示无可靠辅币位定义。
     */
    private Integer fractionDigits;

    /**
     * 主币转换为最小辅币单位的倍数，例如 USD 为 100，JPY 为 1。
     */
    private Long minorUnitMultiplier;

    /**
     * 最小金额单位，例如 USD 为 0.01，JPY 为 1。
     */
    private BigDecimal minimumAmount;

    /**
     * 状态：1 启用，0 停用。
     */
    private Integer status;

    /**
     * 创建时间，数据库统一使用 UTC+8。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，数据库统一使用 UTC+8。
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标识：0 未删除，1 已删除。
     */
    private Integer deleted;
}
