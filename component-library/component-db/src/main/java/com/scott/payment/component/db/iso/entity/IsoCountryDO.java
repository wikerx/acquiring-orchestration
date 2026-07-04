package com.scott.payment.component.db.iso.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCountryDO
 * @date : 2026-06-03 14:22
 * @email : scott_x@163.com
 * @description : ISO 3166 国家地区基础字典数据库实体
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCountryDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Iso Country 数据库实体，位于 component-library/component-db 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("base_iso_country")
public class IsoCountryDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 七大洲代码：AS/EU/AF/NA/SA/OC/AN。
     */
    private String continentCode;

    /**
     * 七大洲中文名称。
     */
    private String continentName;

    /**
     * 国家或地区英文全称。
     */
    private String englishName;

    /**
     * 国家或地区英文简称。
     */
    private String shortEnglishName;

    /**
     * 国家或地区中文名称。
     */
    private String chineseName;

    /**
     * ISO 3166-1 alpha-2 两位字母代码。
     */
    private String alpha2Code;

    /**
     * ISO 3166-1 alpha-3 三位字母代码。
     */
    private String alpha3Code;

    /**
     * ISO 3166-1 numeric 三位数字代码。
     */
    private String numericCode;

    /**
     * 国家或地区图标。
     */
    private String flagEmoji;

    /**
     * 主要语言代码，非 ISO 3166 强制字段。
     */
    private String primaryLanguageCode;

    /**
     * 主要语言英文名称。
     */
    private String primaryLanguageEnglish;

    /**
     * 主要语言中文名称。
     */
    private String primaryLanguageChinese;

    /**
     * 默认币种 ISO 4217 三位字母代码。
     */
    private String currencyAlpha3Code;

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
