package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;


@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountExportRow
 * @date : 2026-06-20 01:15
 * @email : scott_x@163.com
 * @description : Sys User Account Export Row 协作组件，位于 运营后台服务，封装 sys用户账号exportrow 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
public class SysUserAccountExportRow {

    /**
     * 登录账号。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.user.loginAccount", width = 20)
    private String loginAccount;

    /**
     * 用户真实姓名。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.user.realName", width = 18)
    private String realName;

    /**
     * 所属部门名称。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.user.deptName", width = 18)
    private String deptName;

    /**
     * 所属岗位名称，多个岗位按当前语言分隔。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.user.postNames", width = 24)
    private String postNamesText;

    /**
     * 已绑定角色名称，多个角色按当前语言分隔。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.user.roleNames", width = 28)
    private String roleNamesText;

    /**
     * 手机号。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.user.mobile", width = 18)
    private String mobile;

    /**
     * 邮箱。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.user.email", width = 28)
    private String email;

    /**
     * 启停状态文案。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.user.status", width = 12)
    private String status;

    /**
     * 锁定状态文案。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.user.locked", width = 12)
    private String locked;

    /**
     * 最后登录时间。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.user.lastLoginAt", width = 22)
    private LocalDateTime lastLoginAt;

    /**
     * 最后登录IP。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.user.lastLoginIp", width = 18)
    private String lastLoginIp;

    /**
     * 备注。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.user.remark", width = 28)
    private String remark;

    /**
     * 创建时间。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.user.createdAt", width = 22)
    private LocalDateTime createdAt;
}
