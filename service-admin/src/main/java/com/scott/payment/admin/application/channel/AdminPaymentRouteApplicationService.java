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

    public List<MerchantRouteConflictResponse> merchantConflicts(MerchantRouteConflictRequest request) {
        return paymentRouteService.merchantConflicts(request);
    }

    public RouteRuleResponse createRule(RouteRuleSaveRequest request) {
        return paymentRouteService.createRule(request);
    }

    public RouteRuleResponse updateRule(Long id, RouteRuleSaveRequest request) {
        return paymentRouteService.updateRule(id, request);
    }

    public RouteRuleResponse copyRule(Long id, RouteCopyRequest request) {
        return paymentRouteService.copyRule(id, request);
    }

    public RouteRuleResponse publishRule(Long id, RouteActionRequest request) {
        return paymentRouteService.publishRule(id, request);
    }

    public RouteRuleResponse disableRule(Long id, RouteActionRequest request) {
        return paymentRouteService.disableRule(id, request);
    }

    public void deleteRule(Long id, RouteActionRequest request) {
        paymentRouteService.deleteRule(id, request);
    }

    public RouteValidationResponse validateRule(Long id) {
        return paymentRouteService.validateRule(id);
    }

    public PageResult<RouteChangeLogResponse> pageChangeLogs(Long id, RouteChangeLogQuery query) {
        return paymentRouteService.pageChangeLogs(id, query);
    }
}
