package com.scott.payment.admin.api.channel;

import com.scott.payment.admin.application.channel.AdminPaymentRouteApplicationService;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteActionRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.MerchantRouteConflictRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.MerchantRouteConflictResponse;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteChangeLogQuery;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteChangeLogResponse;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteCopyRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteRuleQuery;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteRuleResponse;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteRuleSaveRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteValidationResponse;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理端支付路由配置接口。
 *
 * <p>接口只维护配置事实；阶段 1A 不向支付链路发布或加载运行时路由。</p>
 */
@RestController
@RequestMapping("/admin/channel/routes")
public class AdminPaymentRouteController {

    private final AdminPaymentRouteApplicationService applicationService;

    public AdminPaymentRouteController(AdminPaymentRouteApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 分页查询支付路由规则。 */
    @PostMapping("/search")
    @RequiresPermission("channel:route:list")
    public CommonResult<PageResult<RouteRuleResponse>> pageRules(@RequestBody(required = false) RouteRuleQuery query) {
        return success(applicationService.pageRules(query));
    }

    /** 查询支付路由规则完整详情。 */
    @GetMapping("/{id}")
    @RequiresPermission("channel:route:detail")
    public CommonResult<RouteRuleResponse> getRule(@PathVariable("id") Long id) {
        return success(applicationService.getRule(id));
    }

    /** 批量检查指定商户已存在的路由配置，供新增和编辑页面提前提示。 */
    @PostMapping("/merchant-conflicts")
    @RequiresPermission("channel:route:detail")
    public CommonResult<List<MerchantRouteConflictResponse>> merchantConflicts(
            @Valid @RequestBody MerchantRouteConflictRequest request) {
        return success(applicationService.merchantConflicts(request));
    }

    /** 创建草稿规则。 */
    @PostMapping
    @RequiresPermission("channel:route:add")
    @OperationLog(moduleName = "支付路由配置", businessType = OperationTypeConstants.CREATE, operation = "新增支付路由规则")
    public CommonResult<RouteRuleResponse> createRule(@Valid @RequestBody RouteRuleSaveRequest request) {
        return success(applicationService.createRule(request));
    }

    /** 更新草稿或停用状态的规则；已发布规则禁止直接编辑。 */
    @PutMapping("/{id}")
    @RequiresPermission("channel:route:edit")
    @OperationLog(moduleName = "支付路由配置", businessType = OperationTypeConstants.UPDATE, operation = "修改支付路由规则")
    public CommonResult<RouteRuleResponse> updateRule(@PathVariable("id") Long id,
                                                       @Valid @RequestBody RouteRuleSaveRequest request) {
        return success(applicationService.updateRule(id, request));
    }

    /** 复制现有规则为新草稿。 */
    @PostMapping("/{id}/copy")
    @RequiresPermission("channel:route:copy")
    @OperationLog(moduleName = "支付路由配置", businessType = OperationTypeConstants.CREATE, operation = "复制支付路由规则")
    public CommonResult<RouteRuleResponse> copyRule(@PathVariable("id") Long id,
                                                     @Valid @RequestBody RouteCopyRequest request) {
        return success(applicationService.copyRule(id, request));
    }

    /** 发布规则，执行候选可用性与同优先级重叠检查。 */
    @PostMapping("/{id}/publish")
    @RequiresPermission("channel:route:publish")
    @OperationLog(moduleName = "支付路由配置", businessType = OperationTypeConstants.UPDATE, operation = "发布支付路由规则")
    public CommonResult<RouteRuleResponse> publishRule(@PathVariable("id") Long id,
                                                        @Valid @RequestBody RouteActionRequest request) {
        return success(applicationService.publishRule(id, request));
    }

    /** 停用已发布规则。 */
    @PostMapping("/{id}/disable")
    @RequiresPermission("channel:route:disable")
    @OperationLog(moduleName = "支付路由配置", businessType = OperationTypeConstants.UPDATE, operation = "停用支付路由规则")
    public CommonResult<RouteRuleResponse> disableRule(@PathVariable("id") Long id,
                                                        @Valid @RequestBody RouteActionRequest request) {
        return success(applicationService.disableRule(id, request));
    }

    /** 删除未发布规则，保留不可变变更日志。 */
    @DeleteMapping("/{id}")
    @RequiresPermission("channel:route:remove")
    @OperationLog(moduleName = "支付路由配置", businessType = OperationTypeConstants.DELETE, operation = "删除支付路由规则")
    public CommonResult<Void> deleteRule(@PathVariable("id") Long id,
                                         @Valid @RequestBody RouteActionRequest request) {
        applicationService.deleteRule(id, request);
        return success();
    }

    /** 获取发布前配置校验结果，不修改规则状态。 */
    @GetMapping("/{id}/validation")
    @RequiresPermission("channel:route:validate")
    public CommonResult<RouteValidationResponse> validateRule(@PathVariable("id") Long id) {
        return success(applicationService.validateRule(id));
    }

    /** 分页查询路由规则变更历史。 */
    @PostMapping("/{id}/change-logs/search")
    @RequiresPermission("channel:route:history")
    public CommonResult<PageResult<RouteChangeLogResponse>> pageChangeLogs(
            @PathVariable("id") Long id,
            @RequestBody(required = false) RouteChangeLogQuery query) {
        return success(applicationService.pageChangeLogs(id, query));
    }
}
