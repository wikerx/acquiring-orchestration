package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys User Account Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysUserAccountExportRow {

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.user.loginAccount", width = 20)
    private String loginAccount;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.user.realName", width = 18)
    private String realName;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.user.deptName", width = 18)
    private String deptName;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.user.postNames", width = 24)
    private String postNamesText;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.user.mobile", width = 18)
    private String mobile;

    /**
     * 系统管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.user.email", width = 28)
    private String email;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.user.status", width = 12)
    private String status;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.user.locked", width = 12)
    private String locked;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.user.lastLoginAt", width = 22)
    private LocalDateTime lastLoginAt;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.user.lastLoginIp", width = 18)
    private String lastLoginIp;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.user.createdAt", width = 22)
    private LocalDateTime createdAt;
}
