package com.scott.payment.admin.service;

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
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/** 管理端支付路由配置服务，不参与真实支付选路。 */
public interface AdminPaymentRouteService {

    PageResult<RouteRuleResponse> pageRules(RouteRuleQuery query);

    RouteRuleResponse getRule(Long id);

    List<MerchantRouteConflictResponse> merchantConflicts(MerchantRouteConflictRequest request);

    RouteRuleResponse createRule(RouteRuleSaveRequest request);

    RouteRuleResponse updateRule(Long id, RouteRuleSaveRequest request);

    RouteRuleResponse copyRule(Long id, RouteCopyRequest request);

    RouteRuleResponse publishRule(Long id, RouteActionRequest request);

    RouteRuleResponse disableRule(Long id, RouteActionRequest request);

    void deleteRule(Long id, RouteActionRequest request);

    RouteValidationResponse validateRule(Long id);

    PageResult<RouteChangeLogResponse> pageChangeLogs(Long id, RouteChangeLogQuery query);
}
