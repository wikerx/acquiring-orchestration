package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 系统参数导出行对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Config Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysConfigExportRow {

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.config.configName", width = 20)
    private String configName;

    /**
     * 系统管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.config.configKey", width = 24)
    private String configKey;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.config.configGroup", width = 18)
    private String configGroup;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.config.configValue", width = 28)
    private String configValue;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.config.status", width = 12)
    private String status;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.config.createdAt", width = 22)
    private LocalDateTime createdAt;
}
