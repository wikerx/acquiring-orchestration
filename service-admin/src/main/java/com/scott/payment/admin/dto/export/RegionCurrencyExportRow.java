package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RegionCurrencyExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 地区币种配置导出行对象
 * @status : create
 */
@Data
public class RegionCurrencyExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.regionCurrency.alpha2Code", width = 14)
    /**
     * alpha 2 Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String alpha2Code;

    @ExcelExportColumn(order = 2, headerKey = "excel.regionCurrency.countryName", width = 20)
    /**
     * country Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值必须来自平台支持国家地区；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String countryName;

    @ExcelExportColumn(order = 3, headerKey = "excel.regionCurrency.continentName", width = 16)
    /**
     * continent Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private String continentName;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.regionCurrency.currencyAlpha3Code", width = 16)
    private String currencyAlpha3Code;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.regionCurrency.currencyName", width = 20)
    private String currencyName;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.regionCurrency.currencySymbol", width = 12)
    private String currencySymbol;

    @ExcelExportColumn(order = 7, headerKey = "excel.regionCurrency.status", width = 12)
    /**
     * status，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private String status;
}
