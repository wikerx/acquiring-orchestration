package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysUserAccountCreateRequest;
import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.SysUserAccountQueryRequest;
import com.scott.payment.admin.dto.SysUserAccountResetPasswordRequest;
import com.scott.payment.admin.dto.SysUserAccountStatusRequest;
import com.scott.payment.admin.dto.SysUserAccountUpdateRequest;
import com.scott.payment.admin.dto.SysUserRoleAuthDTO;
import com.scott.payment.admin.dto.SysUserRoleGrantRequest;
import com.scott.payment.admin.dto.export.SysUserAccountExportRow;
import com.scott.payment.admin.converter.UserExportConverter;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.admin.service.AdminUserService;
import com.scott.payment.component.core.model.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台用户管理应用服务
 * @status : create
 *
 * <p>当前应用层只负责收敛控制器入口，具体用户、角色、密码和状态规则仍由领域服务承载。</p>
 */
@Service
public class AdminUserApplicationService {

    /**
     * 后台用户领域服务。
     */
    private final AdminUserService adminUserService;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建后台用户应用服务。
     *
     * @param adminUserService 后台用户领域服务
     */
    public AdminUserApplicationService(AdminUserService adminUserService,
                                       ExcelExportService excelExportService,
                                       ExcelI18nMessageResolver excelI18nMessageResolver,
                                       ExcelLocaleResolver excelLocaleResolver) {
        this.adminUserService = adminUserService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 分页查询后台用户。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResult<SysUserAccountDTO> pageUsers(SysUserAccountQueryRequest request) {
        return adminUserService.pageUsers(request);
    }

    /**
     * 导出后台用户列表。
     *
     * @param request 查询条件
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportUsers(SysUserAccountQueryRequest request,
                            String operator,
                            HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<SysUserAccountExportRow> rows = adminUserService.listUsers(request).stream()
                .map(UserExportConverter.INSTANCE::toExportRow)
                .peek(row -> fillUserExportDisplayValue(row, locale))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<SysUserAccountExportRow>builder()
                        .fileName("用户列表_" + timestampSuffix())
                        .sheetName("用户列表")
                        .titleKey("excel.user.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(buildUserQuerySummary(request, locale))
                        .rowClass(SysUserAccountExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 新增后台用户。
     *
     * @param request 新增请求
     * @return 新增后的用户
     */
    public SysUserAccountDTO createUser(SysUserAccountCreateRequest request) {
        return adminUserService.createUser(request);
    }

    /**
     * 更新后台用户。
     *
     * @param request 更新请求
     * @return 更新后的用户
     */
    public SysUserAccountDTO updateUser(SysUserAccountUpdateRequest request) {
        return adminUserService.updateUser(request);
    }

    /**
     * 更新后台用户状态。
     *
     * @param request 状态变更请求
     */
    public void updateStatus(SysUserAccountStatusRequest request) {
        adminUserService.updateStatus(request);
    }

    /**
     * 重置后台用户密码。
     *
     * @param request 重置密码请求
     */
    public void resetPassword(SysUserAccountResetPasswordRequest request) {
        adminUserService.resetPassword(request);
    }

    /**
     * 查询后台用户角色授权。
     *
     * @param accountId 用户账号ID
     * @return 角色授权信息
     */
    public SysUserRoleAuthDTO userRoles(Long accountId) {
        return adminUserService.userRoles(accountId);
    }

    /**
     * 分配后台用户角色。
     *
     * @param request 角色分配请求
     */
    public void grantRoles(SysUserRoleGrantRequest request) {
        adminUserService.grantRoles(request);
    }

    /**
     * 删除后台用户。
     *
     * @param accountIds 账号主键列表
     */
    public void removeUsers(List<Long> accountIds) {
        adminUserService.removeUsers(accountIds);
    }

    /**
     * 填充用户导出显示文案。
     *
     * @param row 导出行对象
     * @param locale 当前语言
     */
    private void fillUserExportDisplayValue(SysUserAccountExportRow row, Locale locale) {
        row.setStatus(resolveStatusText("1".equals(String.valueOf(row.getStatus())), locale));
        row.setLocked(resolveBooleanText("1".equals(String.valueOf(row.getLocked())), locale));
    }

    /**
     * 构造用户查询摘要。
     *
     * @param request 查询条件
     * @param locale 当前语言
     * @return 查询摘要
     */
    private String buildUserQuerySummary(SysUserAccountQueryRequest request, Locale locale) {
        if (request == null) {
            return excelI18nMessageResolver.resolve("excel.common.noCondition", locale);
        }
        StringBuilder builder = new StringBuilder();
        if (request.getLoginAccount() != null && !request.getLoginAccount().isBlank()) {
            builder.append("登录账号=").append(request.getLoginAccount().trim());
        }
        if (request.getStatus() != null) {
            if (!builder.isEmpty()) {
                builder.append("，");
            }
            builder.append("状态=").append(resolveStatusText(request.getStatus() == 1, locale));
        }
        return builder.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : builder.toString();
    }

    /**
     * 生成导出文件时间后缀。
     *
     * @return 时间后缀
     */
    private String timestampSuffix() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
    }

    /**
     * 解析启停状态文案。
     *
     * @param enabled 是否启用
     * @param locale 当前语言
     * @return 状态文案
     */
    private String resolveStatusText(boolean enabled, Locale locale) {
        return excelI18nMessageResolver.resolve(enabled ? "excel.common.enabled" : "excel.common.disabled", locale);
    }

    /**
     * 解析是/否文案。
     *
     * @param value 布尔值
     * @param locale 当前语言
     * @return 文案
     */
    private String resolveBooleanText(boolean value, Locale locale) {
        return excelI18nMessageResolver.resolve(value ? "excel.common.yes" : "excel.common.no", locale);
    }
}
