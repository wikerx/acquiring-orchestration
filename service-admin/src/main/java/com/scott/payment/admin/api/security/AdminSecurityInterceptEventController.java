package com.scott.payment.admin.api.security;

import com.scott.payment.admin.application.security.AdminSecurityInterceptEventApplicationService;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventMarkRequest;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventQuery;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventResponse;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSecurityInterceptEventController
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 安全拦截事件后台接口，位于 service-admin 接口层，负责权限校验、HTTP 映射和参数接收。
 * @status : create
 */
@RestController
@RequestMapping("/admin/security/intercept-events")
public class AdminSecurityInterceptEventController {

    /**
     * event Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminSecurityInterceptEventApplicationService eventApplicationService;

    /**
     * 创建安全拦截事件后台控制器。
     *
     * @param eventApplicationService 安全事件应用服务
     */
    public AdminSecurityInterceptEventController(AdminSecurityInterceptEventApplicationService eventApplicationService) {
        this.eventApplicationService = eventApplicationService;
    }

    /**
     * 分页查询安全拦截事件。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("security:intercept-event:list")
    public CommonResult<PageResult<SecurityInterceptEventResponse>> pageEvents(@RequestBody(required = false) SecurityInterceptEventQuery query) {
        return success(eventApplicationService.pageEvents(query));
    }

    /**
     * 按当前查询条件导出安全拦截事件。
     *
     * @param query    查询条件
     * @param response HTTP 响应
     */
    @PostMapping("/export")
    @RequiresPermission("security:intercept-event:export")
    @OperationLog(moduleName = "安全拦截事件", businessType = OperationTypeConstants.EXPORT, operation = "导出安全拦截事件")
    public void exportEvents(@RequestBody(required = false) SecurityInterceptEventQuery query,
                             HttpServletResponse response) {
        eventApplicationService.exportEvents(query, currentOperatorName(), response);
    }

    /**
     * 查询安全拦截事件详情。
     *
     * @param id 事件主键
     * @return 事件详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("security:intercept-event:detail")
    public CommonResult<SecurityInterceptEventResponse> getEvent(@PathVariable("id") Long id) {
        return success(eventApplicationService.getEvent(id));
    }

    /**
     * 标记安全拦截事件处理状态。
     *
     * @param id      事件主键
     * @param request 处理请求
     * @return 更新后的事件详情
     */
    @PutMapping("/{id}/mark")
    @RequiresPermission("security:intercept-event:mark")
    @OperationLog(moduleName = "安全拦截事件", businessType = OperationTypeConstants.UPDATE, operation = "标记安全拦截事件")
    public CommonResult<SecurityInterceptEventResponse> markEvent(@PathVariable("id") Long id,
                                                                  @Valid @RequestBody SecurityInterceptEventMarkRequest request) {
        return success(eventApplicationService.markEvent(id, request));
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount();
    }
}
