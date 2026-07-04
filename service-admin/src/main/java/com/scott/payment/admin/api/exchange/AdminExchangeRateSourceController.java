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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 汇率管理Admin Exchange Rate Source 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/exchange")
public class AdminExchangeRateSourceController {

    /**
     * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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
    /**
     * 获取汇率管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 创建或保存汇率管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 更新汇率管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 更新汇率管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 删除汇率管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
