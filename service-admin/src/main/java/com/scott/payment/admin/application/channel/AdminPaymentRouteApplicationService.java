package com.scott.payment.admin.application.channel;

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
import com.scott.payment.admin.service.AdminPaymentRouteService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;

/** 管理端支付路由配置应用服务，编排接口层与配置领域服务。 */
@Service
public class AdminPaymentRouteApplicationService {

    private final AdminPaymentRouteService paymentRouteService;

    public AdminPaymentRouteApplicationService(AdminPaymentRouteService paymentRouteService) {
        this.paymentRouteService = paymentRouteService;
    }

    public PageResult<RouteRuleResponse> pageRules(RouteRuleQuery query) {
        return paymentRouteService.pageRules(query);
    }

    public RouteRuleResponse getRule(Long id) {
        return paymentRouteService.getRule(id);
    }

    /** 检查商户当前绑定下的候选冲突，辅助配置校验，不自动发布规则。 */
    public List<MerchantRouteConflictResponse> merchantConflicts(MerchantRouteConflictRequest request) {
        return paymentRouteService.merchantConflicts(request);
    }

    /** 创建路由规则草稿，经校验和发布后才可进入运行时配置。 */
    public RouteRuleResponse createRule(RouteRuleSaveRequest request) {
        return paymentRouteService.createRule(request);
    }

    /** 更新规则配置并保留变更审计，发布资格仍需独立校验。 */
    public RouteRuleResponse updateRule(Long id, RouteRuleSaveRequest request) {
        return paymentRouteService.updateRule(id, request);
    }

    /** 从既有规则复制可编辑配置，不把复制动作视为发布授权。 */
    public RouteRuleResponse copyRule(Long id, RouteCopyRequest request) {
        return paymentRouteService.copyRule(id, request);
    }

    /** 校验规则及当前候选能力后发布，并记录操作者和变更原因。 */
    public RouteRuleResponse publishRule(Long id, RouteActionRequest request) {
        return paymentRouteService.publishRule(id, request);
    }

    /** 停用规则并记录原因，运行时选路不得继续使用失效配置。 */
    public RouteRuleResponse disableRule(Long id, RouteActionRequest request) {
        return paymentRouteService.disableRule(id, request);
    }

    /** 按规则当前状态检查可删除性并留痕，不删除交易历史。 */
    public void deleteRule(Long id, RouteActionRequest request) {
        paymentRouteService.deleteRule(id, request);
    }

    /** 只校验规则和候选是否具备发布条件，不修改发布状态。 */
    public RouteValidationResponse validateRule(Long id) {
        return paymentRouteService.validateRule(id);
    }

    /** 分页读取规则配置及状态变更审计，保留操作者与原因。 */
    public PageResult<RouteChangeLogResponse> pageChangeLogs(Long id, RouteChangeLogQuery query) {
        return paymentRouteService.pageChangeLogs(id, query);
    }
}
