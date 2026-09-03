package com.scott.payment.admin.api.exchange;

import com.scott.payment.admin.application.exchange.AdminExchangeRateApplicationService;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.StatusRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * @classname : AdminExchangeRateSourceController
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : admin汇率汇率来源 HTTP 控制器，位于 运营后台服务，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 * @status : create
 */
@RestController
@RequestMapping("/admin/exchange")
public class AdminExchangeRateSourceController {

    private final AdminExchangeRateApplicationService applicationService;

    public AdminExchangeRateSourceController(AdminExchangeRateApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询汇率源配置，供汇率源管理页面展示。
     *
     * @param query 汇率源查询条件，允许为空
     * @return 汇率源分页结果
     */
    @PostMapping("/sources/search")
    @RequiresPermission("exchange:source:list")
    public CommonResult<PageResult<SourceResponse>> pageSources(@RequestBody(required = false) SourceQuery query) {
        return success(applicationService.pageSources(query));
    }

    /**
     * 导出汇率源配置。
     *
     * @param query    查询条件，允许为空
     * @param response HTTP 响应
     */
    @PostMapping("/sources/export")
    @RequiresPermission("exchange:source:export")
    @OperationLog(moduleName = "汇率源管理", businessType = OperationTypeConstants.EXPORT, operation = "导出汇率源")
    public void exportSources(@RequestBody(required = false) SourceQuery query,
                              HttpServletResponse response) {
        applicationService.exportSources(query, currentOperatorName(), response);
    }

    /**
     * 查询单个汇率源配置详情。
     *
     * @param id 汇率源主键
     * @return 汇率源详情
     */
    @GetMapping("/sources/{id}")
    @RequiresPermission("exchange:source:detail")
    public CommonResult<SourceResponse> getSource(@PathVariable("id") Long id) {
        return success(applicationService.getSource(id));
    }

    /**
     * 新增外部汇率源配置。
     *
     * @param request 汇率源保存请求
     * @return 新增后的汇率源详情
     */
    @PostMapping("/sources")
    @RequiresPermission("exchange:source:add")
    @OperationLog(moduleName = "汇率源管理", businessType = OperationTypeConstants.CREATE, operation = "新增汇率源")
    public CommonResult<SourceResponse> createSource(@Valid @RequestBody SourceSaveRequest request) {
        return success(applicationService.createSource(request));
    }

    /**
     * 修改外部汇率源配置。
     *
     * @param id      汇率源主键
     * @param request 汇率源保存请求
     * @return 修改后的汇率源详情
     */
    @PutMapping("/sources/{id}")
    @RequiresPermission("exchange:source:edit")
    @OperationLog(moduleName = "汇率源管理", businessType = OperationTypeConstants.UPDATE, operation = "修改汇率源")
    public CommonResult<SourceResponse> updateSource(@PathVariable("id") Long id,
                                                     @Valid @RequestBody SourceSaveRequest request) {
        return success(applicationService.updateSource(id, request));
    }

    /**
     * 启用或停用汇率源。
     *
     * @param id      汇率源主键
     * @param request 状态请求，1 表示启用，0 表示停用
     * @return 切换状态后的汇率源详情
     */
    @PutMapping("/sources/{id}/status")
    @RequiresPermission("exchange:source:status")
    @OperationLog(moduleName = "汇率源管理", businessType = OperationTypeConstants.UPDATE, operation = "切换汇率源状态")
    public CommonResult<SourceResponse> updateSourceStatus(@PathVariable("id") Long id,
                                                           @Valid @RequestBody StatusRequest request) {
        return success(applicationService.updateSourceStatus(id, request.getStatus()));
    }

    /**
     * 删除未被原始汇率、规则或业务汇率引用的汇率源。
     *
     * @param id 汇率源主键
     * @return 空结果
     */
    @DeleteMapping("/sources/{id}")
    @RequiresPermission("exchange:source:remove")
    @OperationLog(moduleName = "汇率源管理", businessType = OperationTypeConstants.DELETE, operation = "删除汇率源")
    public CommonResult<Void> deleteSource(@PathVariable("id") Long id) {
        applicationService.deleteSource(id);
        return success();
    }

    /**
     * 获取当前操作人名称，用于写入 Excel 导出元信息。
     *
     * @return 操作人名称
     */
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
