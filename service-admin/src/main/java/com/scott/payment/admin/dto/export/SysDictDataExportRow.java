package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictDataExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 字典数据导出行对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictDataExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Dict Data Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysDictDataExportRow {

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.dictData.dictType", width = 20)
    private String dictType;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.dictData.dictLabel", width = 22)
    private String dictLabel;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.dictData.dictValue", width = 22)
    private String dictValue;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.dictData.locale", width = 14)
    private String locale;

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.dictData.sortNo", width = 10)
    private Integer sortNo;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.dictData.status", width = 12)
    private String status;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.dictData.defaultFlag", width = 12)
    private String defaultFlag;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.dictData.createdAt", width = 22)
    private LocalDateTime createdAt;
}
