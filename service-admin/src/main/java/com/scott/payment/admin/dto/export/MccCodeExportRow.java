package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MccCodeExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Mcc Code Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class MccCodeExportRow {

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.mcc.mccCode", width = 14)
    private String mccCode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.mcc.nameCn", width = 24)
    private String nameCn;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.mcc.nameEn", width = 36)
    private String nameEn;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.mcc.level1", width = 22)
    private String level1Name;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.mcc.level2", width = 22)
    private String level2Name;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.mcc.mccType", width = 18)
    private String mccType;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.mcc.riskLevel", width = 18)
    private String riskLevel;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.mcc.deliveryApplicability", width = 20)
    private String deliveryApplicability;

    /**
     * 收单支付状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.mcc.status", width = 12)
    private String status;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.mcc.source", width = 18)
    private String source;

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.mcc.versionNo", width = 16)
    private String versionNo;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.mcc.effectiveTime", width = 22)
    private LocalDateTime effectiveTime;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.mcc.expireTime", width = 22)
    private LocalDateTime expireTime;

    /**
     * 收单支付备注字段，用于记录人工说明，不参与核心状态流转。
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.mcc.remark", width = 30)
    private String remark;
}
