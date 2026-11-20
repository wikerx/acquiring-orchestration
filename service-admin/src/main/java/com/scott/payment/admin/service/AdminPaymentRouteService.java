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

    /** 分页读取管理端路由配置及状态，不执行真实交易选路。 */
    PageResult<RouteRuleResponse> pageRules(RouteRuleQuery query);

    /** 读取路由规则详情及候选配置，不返回渠道密钥。 */
    RouteRuleResponse getRule(Long id);

    /** 检查商户当前绑定下的候选冲突，辅助配置校验，不自动发布规则。 */
    List<MerchantRouteConflictResponse> merchantConflicts(MerchantRouteConflictRequest request);

    /** 创建路由规则草稿，经校验和发布后才可进入运行时配置。 */
    RouteRuleResponse createRule(RouteRuleSaveRequest request);

    /** 更新规则配置并保留变更审计，发布资格仍需独立校验。 */
    RouteRuleResponse updateRule(Long id, RouteRuleSaveRequest request);

    /** 从既有规则复制可编辑配置，不把复制动作视为发布授权。 */
    RouteRuleResponse copyRule(Long id, RouteCopyRequest request);

    /** 校验规则及当前候选能力后发布，并记录操作者和变更原因。 */
    RouteRuleResponse publishRule(Long id, RouteActionRequest request);

    /** 停用规则并记录原因，运行时选路不得继续使用失效配置。 */
    RouteRuleResponse disableRule(Long id, RouteActionRequest request);

    /** 按规则当前状态检查可删除性并留痕，不删除交易历史。 */
    void deleteRule(Long id, RouteActionRequest request);

    /** 只校验规则和候选是否具备发布条件，不修改发布状态。 */
    RouteValidationResponse validateRule(Long id);

    /** 分页读取规则配置及状态变更审计，保留操作者与原因。 */
    PageResult<RouteChangeLogResponse> pageChangeLogs(Long id, RouteChangeLogQuery query);
}
