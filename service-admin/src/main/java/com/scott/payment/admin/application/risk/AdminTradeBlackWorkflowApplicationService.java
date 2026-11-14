package com.scott.payment.admin.application.risk;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.client.payment.dto.TradeBlackPaymentClientDTOs;
import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.admin.dto.risk.TradeBlackWorkflowDTOs;
import com.scott.payment.admin.mapper.AdminTradeBlackWorkflowMapper;
import com.scott.payment.admin.mapper.RiskManagementMapper;
import com.scott.payment.admin.support.risk.RiskFunctionDefinition;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCountryResolver;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 系统交易加黑新版工作流。
 *
 * <p>浏览器只提交交易定位条件、目标作用域、操作和功能码。该服务从支付核心读取可信
 * 交易事实，调用统一名单归一化与真实名单写入链路，并只向浏览器返回脱敏候选项。</p>
 */
@Service
public class AdminTradeBlackWorkflowApplicationService {

    private static final String GLOBAL = "GLOBAL";
    private static final String MERCHANT = "MERCHANT";
    private static final String ADD = "ADD";
    private static final String RELEASE = "RELEASE";
    private static final int INTERNAL_BATCH_SIZE = 500;
    private static final int MAX_TARGET_MERCHANTS = 20;

    private final PaymentInternalClient paymentInternalClient;
    private final AdminRiskManagementApplicationService riskManagementApplicationService;
    private final RiskManagementMapper riskManagementMapper;
    private final AdminTradeBlackWorkflowMapper workflowMapper;

    public AdminTradeBlackWorkflowApplicationService(
            PaymentInternalClient paymentInternalClient,
            AdminRiskManagementApplicationService riskManagementApplicationService,
            RiskManagementMapper riskManagementMapper,
            AdminTradeBlackWorkflowMapper workflowMapper) {
        this.paymentInternalClient = paymentInternalClient;
        this.riskManagementApplicationService = riskManagementApplicationService;
        this.riskManagementMapper = riskManagementMapper;
        this.workflowMapper = workflowMapper;
    }

    /** 解析交易、自动计算名单值并返回脱敏候选项。 */
    public TradeBlackWorkflowDTOs.PreviewResponse preview(TradeBlackWorkflowDTOs.PreviewRequest request) {
        PreparedPreview prepared = prepare(request);
        return prepared.response();
    }

    /**
     * 重新解析候选项并执行真实名单变更。selectedCandidateKeys 只作为选择集合，
     * 不携带任何名单值，不能伪造后端哈希。
     */
    public TradeBlackWorkflowDTOs.ExecuteResponse execute(TradeBlackWorkflowDTOs.ExecuteRequest request) {
        if (request == null || request.getWorkflow() == null) {
            throw invalid("交易加黑工作流不能为空");
        }
        if (!StringUtils.hasText(request.getRequestId())) {
            throw invalid("请求幂等号不能为空");
        }
        validate(request.getWorkflow());
        Set<String> selectedKeys = request.getSelectedCandidateKeys() == null
                ? Set.of() : new LinkedHashSet<>(request.getSelectedCandidateKeys());
        if (selectedKeys.isEmpty()) {
            throw invalid("请选择需要执行的候选项");
        }
        Long operatorAccountId = currentOperatorAccountId();
        String requestFingerprint = requestFingerprint(request, selectedKeys);
        Map<String, Object> existing = workflowMapper.selectBatchByRequestId(
                trim(request.getRequestId()), operatorAccountId);
        if (existing != null && !existing.isEmpty()) {
            return existingBatch(existing, requestFingerprint);
        }

        PreparedPreview prepared = prepare(request.getWorkflow());
        List<CandidatePlan> selectedPlans = prepared.plans().stream()
                .filter(plan -> selectedKeys.contains(plan.view().getCandidateKey()))
                .filter(plan -> plan.view().isSelectable())
                .toList();
        if (selectedPlans.isEmpty()) {
            throw invalid("所选候选项已不可执行，请重新预览");
        }
        if (selectedPlans.size() != selectedKeys.size()) {
            throw invalid("候选项已变化，请重新预览后再执行");
        }

        String batchNo = "TB" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("batchNo", batchNo);
        batch.put("requestId", trim(request.getRequestId()));
        batch.put("requestFingerprint", requestFingerprint);
        batch.put("inputMode", upper(request.getWorkflow().getInputMode()));
        batch.put("scopeType", upper(request.getWorkflow().getScopeType()));
        batch.put("actionType", upper(request.getWorkflow().getActionType()));
        batch.put("functionCount", distinctFunctions(request.getWorkflow()).size());
        batch.put("orderCount", request.getWorkflow().getOrders().size());
        try {
            workflowMapper.insertBatch(batch, operatorAccountId, currentOperatorName());
        } catch (DuplicateKeyException exception) {
            Map<String, Object> duplicate = workflowMapper.selectBatchByRequestId(
                    trim(request.getRequestId()), operatorAccountId);
            if (duplicate != null && !duplicate.isEmpty()) {
                return existingBatch(duplicate, requestFingerprint);
            }
            throw exception;
        }

        TradeBlackWorkflowDTOs.ExecuteResponse response = new TradeBlackWorkflowDTOs.ExecuteResponse();
        response.setBatchNo(batchNo);
        response.setTotalCount(selectedPlans.size());
        for (CandidatePlan candidate : selectedPlans) {
            executeOne(batchNo, request.getWorkflow(), candidate, response);
        }
        String status = response.getFailureCount() == 0
                ? "SUCCESS"
                : response.getSuccessCount() + response.getSkippedCount() > 0 ? "PARTIAL_SUCCESS" : "FAILED";
        response.setStatus(status);
        workflowMapper.finishBatch(batchNo, status, response.getTotalCount(), response.getSuccessCount(),
                response.getSkippedCount(), response.getFailureCount());
        return response;
    }

    private void executeOne(String batchNo,
                            TradeBlackWorkflowDTOs.PreviewRequest workflow,
                            CandidatePlan candidate,
                            TradeBlackWorkflowDTOs.ExecuteResponse response) {
        String resultCode;
        String message;
        try {
            resultCode = riskManagementApplicationService.applyDerivedList(
                    candidate.plan(), workflow.getActionType(), workflow.getActionReason());
            message = resultMessage(resultCode);
            if ("ADDED".equals(resultCode) || "RELEASED".equals(resultCode)) {
                response.setSuccessCount(response.getSuccessCount() + 1);
                insertLegacyAudit(batchNo, workflow, candidate, resultCode);
            } else {
                response.setSkippedCount(response.getSkippedCount() + 1);
            }
        } catch (Exception exception) {
            resultCode = "FAILED";
            message = safeFailureMessage(exception);
            response.setFailureCount(response.getFailureCount() + 1);
            response.getFailures().add(toExecuteItem(candidate.view(), resultCode, message));
        }
        insertBatchItem(batchNo, candidate.view(), resultCode, message);
    }

    private PreparedPreview prepare(TradeBlackWorkflowDTOs.PreviewRequest request) {
        validate(request);
        List<TradeBlackPaymentClientDTOs.FactItem> facts = resolveFacts(request.getOrders());
        TradeBlackWorkflowDTOs.PreviewResponse response = new TradeBlackWorkflowDTOs.PreviewResponse();
        response.setOrderCount(request.getOrders().size());
        List<CandidatePlan> plans = new ArrayList<>();
        for (TradeBlackPaymentClientDTOs.FactItem fact : facts) {
            if (!fact.isFound()) {
                response.getOrderErrors().add(orderError(fact));
                continue;
            }
            response.setResolvedOrderCount(response.getResolvedOrderCount() + 1);
            plans.addAll(candidatePlans(request, fact));
            if (plans.size() > TradeBlackWorkflowDTOs.MAX_SELECTED_CANDIDATES) {
                throw invalid("候选项超过20000条，请减少订单、商户范围或黑名单功能后重试");
            }
        }
        response.setCandidates(plans.stream().map(CandidatePlan::view).toList());
        response.setCandidateCount(plans.size());
        response.setSelectableCount((int) plans.stream().filter(plan -> plan.view().isSelectable()).count());
        response.setUnavailableCount((int) plans.stream().filter(plan -> "NOT_AVAILABLE".equals(plan.view().getState())).count());
        return new PreparedPreview(response, plans);
    }

    private List<CandidatePlan> candidatePlans(TradeBlackWorkflowDTOs.PreviewRequest workflow,
                                               TradeBlackPaymentClientDTOs.FactItem fact) {
        List<CandidatePlan> result = new ArrayList<>();
        List<TargetScope> scopes = targetScopes(workflow);
        for (String functionCode : distinctFunctions(workflow)) {
            List<RawCandidate> rawCandidates = extract(functionCode, fact);
            if (rawCandidates.isEmpty()) {
                for (TargetScope scope : scopes) {
                    result.add(unavailable(workflow, fact, functionCode, scope,
                            unavailableMessage(functionCode, fact)));
                }
                continue;
            }
            for (RawCandidate raw : rawCandidates) {
                for (TargetScope scope : scopes) {
                    result.add(prepareCandidate(workflow, fact, raw, scope));
                }
            }
        }
        return deduplicateCandidates(result);
    }

    /**
     * 同一交易的账单、付款人和收货快照可能包含相同值。名单实际匹配键相同时只展示和执行一次，
     * 同时合并来源说明，避免操作员误以为需要重复处理同一个对象。
     */
    private List<CandidatePlan> deduplicateCandidates(List<CandidatePlan> candidates) {
        Map<String, CandidatePlan> unique = new LinkedHashMap<>();
        for (CandidatePlan candidate : candidates) {
            String key = candidate.plan() == null
                    ? candidate.view().getCandidateKey()
                    : String.join("|",
                    value(candidate.view().getTransactionId()),
                    value(candidate.view().getFunctionCode()),
                    value(candidate.view().getTargetScope()),
                    value(candidate.view().getTargetMerchantId()),
                    value(candidate.plan().matchKey()));
            CandidatePlan existing = unique.get(key);
            if (existing == null) {
                unique.put(key, candidate);
            } else if (candidate.plan() != null) {
                existing.view().setSourceLabel(mergeSourceLabel(
                        existing.view().getSourceLabel(), candidate.view().getSourceLabel()));
            }
        }
        return new ArrayList<>(unique.values());
    }

    private String mergeSourceLabel(String current, String additional) {
        if (!StringUtils.hasText(current)) {
            return trim(additional);
        }
        if (!StringUtils.hasText(additional) || current.equals(additional)
                || List.of(current.split("、")).contains(additional)) {
            return current;
        }
        return current + "、" + additional;
    }

    private CandidatePlan prepareCandidate(TradeBlackWorkflowDTOs.PreviewRequest workflow,
                                           TradeBlackPaymentClientDTOs.FactItem fact,
                                           RawCandidate raw,
                                           TargetScope scope) {
        try {
            AdminRiskManagementApplicationService.DerivedListPlan plan;
            if (raw.region() != null) {
                RiskDTOs.RegionSaveRequest request = new RiskDTOs.RegionSaveRequest();
                request.setMerchantScope(scope.scope());
                request.setMerchantId(scope.merchantId());
                request.setCountryAlpha2(countryAlpha2(raw.region().country()));
                request.setRegionMatchLevel(raw.region().level());
                request.setStateProvinceName(raw.region().state());
                request.setCityName(raw.region().city());
                request.setRemark(workflow.getActionReason());
                plan = riskManagementApplicationService.prepareDerivedRegion(request, workflow.getActionType());
            } else {
                RiskDTOs.RiskListSaveRequest request = new RiskDTOs.RiskListSaveRequest();
                request.setMatchValuePlain(raw.rawValue());
                if ("cardBin".equals(raw.functionCode()) || "ip".equals(raw.functionCode())) {
                    request.setMatchValueStart(raw.rawValue());
                    request.setMatchValueEnd(raw.rawValue());
                }
                if (raw.functionCode().contains("Country")) {
                    request.setCountryAlpha2(countryAlpha2(raw.rawValue()));
                }
                request.setRemark(workflow.getActionReason());
                plan = riskManagementApplicationService.prepareDerivedList(
                        raw.functionCode(), scope.scope(), scope.merchantId(), request,
                        raw.trustedMasked(), raw.trustedHash(), raw.trustedCardBrand(), workflow.getActionType());
            }
            return available(workflow, fact, raw, scope, plan);
        } catch (ServiceException exception) {
            return unavailable(workflow, fact, raw.functionCode(), scope, safeFailureMessage(exception), raw);
        }
    }

    private CandidatePlan available(TradeBlackWorkflowDTOs.PreviewRequest workflow,
                                    TradeBlackPaymentClientDTOs.FactItem fact,
                                    RawCandidate raw,
                                    TargetScope scope,
                                    AdminRiskManagementApplicationService.DerivedListPlan plan) {
        boolean add = ADD.equals(upper(workflow.getActionType()));
        boolean active = plan.activeId() != null;
        String state = add ? active ? "ALREADY_EXISTS" : "READY" : active ? "READY" : "ALREADY_MISSING";
        boolean selectable = "READY".equals(state);
        TradeBlackWorkflowDTOs.Candidate view = baseCandidate(fact, raw.functionCode(), scope, raw);
        view.setMaskedValue(plan.maskedValue());
        view.setState(state);
        view.setStateMessage(stateMessage(state));
        view.setSelectable(selectable);
        view.setSelected(selectable);
        view.setCandidateKey(candidateKey(fact, raw.functionCode(), raw.sourceField(), scope, plan.matchKey()));
        return new CandidatePlan(view, plan);
    }

    private CandidatePlan unavailable(TradeBlackWorkflowDTOs.PreviewRequest workflow,
                                      TradeBlackPaymentClientDTOs.FactItem fact,
                                      String functionCode,
                                      TargetScope scope,
                                      String message) {
        return unavailable(workflow, fact, functionCode, scope, message,
                new RawCandidate(functionCode, "transaction", "交易快照", null, null, null, null, null));
    }

    private CandidatePlan unavailable(TradeBlackWorkflowDTOs.PreviewRequest workflow,
                                      TradeBlackPaymentClientDTOs.FactItem fact,
                                      String functionCode,
                                      TargetScope scope,
                                      String message,
                                      RawCandidate raw) {
        TradeBlackWorkflowDTOs.Candidate view = baseCandidate(fact, functionCode, scope, raw);
        view.setMaskedValue("-");
        view.setState("NOT_AVAILABLE");
        view.setStateMessage(message);
        view.setSelectable(false);
        view.setSelected(false);
        view.setCandidateKey(candidateKey(fact, functionCode, raw.sourceField(), scope, "NOT_AVAILABLE"));
        return new CandidatePlan(view, null);
    }

    private TradeBlackWorkflowDTOs.Candidate baseCandidate(TradeBlackPaymentClientDTOs.FactItem fact,
                                                           String functionCode,
                                                           TargetScope scope,
                                                           RawCandidate raw) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require("BLACK", functionCode);
        TradeBlackWorkflowDTOs.Candidate candidate = new TradeBlackWorkflowDTOs.Candidate();
        candidate.setRowNo(fact.getRowNo());
        candidate.setInputOrderNo(fact.getInputOrderNo());
        candidate.setTransactionId(fact.getTransactionId());
        candidate.setSourceMerchantId(fact.getMerchantId());
        candidate.setMerchantOrderNo(fact.getMerchantOrderNo());
        candidate.setFunctionCode(functionCode);
        candidate.setFunctionName(definition.getFunctionName());
        candidate.setSourceField(raw.sourceField());
        candidate.setSourceLabel(raw.sourceLabel());
        candidate.setTargetScope(scope.scope());
        candidate.setTargetMerchantId(scope.merchantId());
        return candidate;
    }

    private List<RawCandidate> extract(String functionCode, TradeBlackPaymentClientDTOs.FactItem fact) {
        List<RawCandidate> result = new ArrayList<>();
        switch (functionCode) {
            case "cardNo" -> {
                // 交易快照不保存完整 PAN，脱敏卡号和不可逆哈希不能作为可编辑的完整卡号名单值。
            }
            case "cardFingerprint" -> addRaw(result, functionCode, "payment.cardFingerprint", "卡指纹",
                    fact.getPaymentAccountHash());
            case "cardBin" -> addRaw(result, functionCode, "payment.cardBin", "卡BIN",
                    fact.getCardBin(), fact.getPaymentBrand());
            case "cardholderName" -> {
                addRaw(result, functionCode, "billing.cardholderName", "账单持卡人", fullName(fact.getBilling()));
                addRaw(result, functionCode, "payer.name", "付款人姓名", fullName(fact.getPayer()));
                addRaw(result, functionCode, "shipping.name", "收货人姓名", fullName(fact.getShipping()));
            }
            case "phone" -> addContactValues(result, functionCode, fact, ContactField.PHONE);
            case "ip" -> addRaw(result, functionCode, "payer.ipAddress", "付款人IP",
                    fact.getPayer() == null ? null : fact.getPayer().getIpAddress());
            case "region" -> addRegions(result, fact);
            case "email" -> addContactValues(result, functionCode, fact, ContactField.EMAIL);
            case "emailUsername" -> addEmailParts(result, functionCode, fact, EmailPart.USERNAME);
            case "emailDomain" -> addEmailParts(result, functionCode, fact, EmailPart.DOMAIN);
            case "billingAddress" -> {
                addRaw(result, functionCode, "billing.street", "账单地址", value(fact.getBilling(), AddressField.STREET));
                addRaw(result, functionCode, "payer.street", "付款人地址", value(fact.getPayer(), AddressField.STREET));
            }
            case "billingZip" -> {
                addRaw(result, functionCode, "billing.postal", "账单邮编", value(fact.getBilling(), AddressField.POSTAL));
                addRaw(result, functionCode, "payer.postal", "付款人邮编", value(fact.getPayer(), AddressField.POSTAL));
            }
            case "billingCountry" -> {
                addRaw(result, functionCode, "billing.country", "账单国家/地区", value(fact.getBilling(), AddressField.COUNTRY));
                addRaw(result, functionCode, "payer.country", "付款人国家/地区", value(fact.getPayer(), AddressField.COUNTRY));
            }
            case "shippingAddress" -> addRaw(result, functionCode, "shipping.street", "收货地址", value(fact.getShipping(), AddressField.STREET));
            case "shippingZip" -> addRaw(result, functionCode, "shipping.postal", "收货邮编", value(fact.getShipping(), AddressField.POSTAL));
            case "shippingCountry" -> addRaw(result, functionCode, "shipping.country", "收货国家/地区", value(fact.getShipping(), AddressField.COUNTRY));
            case "issuerCountry" -> addRaw(result, functionCode, "payment.issuerCountry", "发卡行国家/地区", fact.getIssuerCountry());
            case "deviceFingerprint" -> {
                // 历史交易未持久化设备指纹，禁止根据其他字段推测。
            }
            default -> throw invalid("不支持的黑名单功能：" + functionCode);
        }
        return result;
    }

    private String unavailableMessage(String functionCode, TradeBlackPaymentClientDTOs.FactItem fact) {
        if ("cardNo".equals(functionCode)
                && (StringUtils.hasText(fact.getCardNumberMasked())
                || StringUtils.hasText(fact.getPaymentAccountHash()))) {
            return "交易仅保存脱敏卡号和不可逆哈希，无法取得完整卡号，本项已跳过";
        }
        return "交易中没有可用于该黑名单功能的数据";
    }

    private void addContactValues(List<RawCandidate> target,
                                  String functionCode,
                                  TradeBlackPaymentClientDTOs.FactItem fact,
                                  ContactField field) {
        addRaw(target, functionCode, "billing." + field.fieldName(), "账单" + field.label(), value(fact.getBilling(), field));
        addRaw(target, functionCode, "payer." + field.fieldName(), "付款人" + field.label(), value(fact.getPayer(), field));
        addRaw(target, functionCode, "shipping." + field.fieldName(), "收货" + field.label(), value(fact.getShipping(), field));
    }

    /**
     * 从可信交易邮箱中派生用户名或域名。完整邮箱仍只在服务端流转，浏览器只接收规范化后的脱敏候选项。
     */
    private void addEmailParts(List<RawCandidate> target,
                               String functionCode,
                               TradeBlackPaymentClientDTOs.FactItem fact,
                               EmailPart part) {
        addEmailPart(target, functionCode, "billing.email", "账单邮箱", value(fact.getBilling(), ContactField.EMAIL), part);
        addEmailPart(target, functionCode, "payer.email", "付款人邮箱", value(fact.getPayer(), ContactField.EMAIL), part);
        addEmailPart(target, functionCode, "shipping.email", "收货邮箱", value(fact.getShipping(), ContactField.EMAIL), part);
    }

    private void addEmailPart(List<RawCandidate> target,
                              String functionCode,
                              String sourceField,
                              String sourceLabel,
                              String email,
                              EmailPart part) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        String normalized = email.trim();
        int atIndex = normalized.indexOf('@');
        if (atIndex <= 0 || atIndex != normalized.lastIndexOf('@') || atIndex == normalized.length() - 1) {
            addRaw(target, functionCode, sourceField, sourceLabel, normalized);
            return;
        }
        String derived = part == EmailPart.USERNAME
                ? normalized.substring(0, atIndex)
                : normalized.substring(atIndex + 1);
        addRaw(target, functionCode, sourceField, sourceLabel, derived);
    }

    private void addRegions(List<RawCandidate> target, TradeBlackPaymentClientDTOs.FactItem fact) {
        addRegion(target, "billing.region", "账单区域", fact.getBilling());
        addRegion(target, "payer.region", "付款人区域", fact.getPayer());
        addRegion(target, "shipping.region", "收货区域", fact.getShipping());
    }

    private void addRegion(List<RawCandidate> target,
                           String sourceField,
                           String sourceLabel,
                           TradeBlackPaymentClientDTOs.PersonAddress address) {
        if (address == null || !StringUtils.hasText(address.getCountry())) {
            return;
        }
        String level = StringUtils.hasText(address.getCity()) ? "CITY"
                : StringUtils.hasText(address.getState()) ? "STATE" : "COUNTRY";
        target.add(new RawCandidate("region", sourceField, sourceLabel, null, null, null, null,
                new RegionValue(address.getCountry(), trim(address.getState()), trim(address.getCity()), level)));
    }

    private void addRaw(List<RawCandidate> target, String functionCode, String sourceField, String sourceLabel, String rawValue) {
        addRaw(target, functionCode, sourceField, sourceLabel, rawValue, null);
    }

    private void addRaw(List<RawCandidate> target,
                        String functionCode,
                        String sourceField,
                        String sourceLabel,
                        String rawValue,
                        String trustedCardBrand) {
        if (StringUtils.hasText(rawValue)) {
            target.add(new RawCandidate(functionCode, sourceField, sourceLabel, rawValue.trim(), null, null,
                    trim(trustedCardBrand), null));
        }
    }

    private List<TradeBlackPaymentClientDTOs.FactItem> resolveFacts(List<TradeBlackWorkflowDTOs.OrderReference> orders) {
        List<TradeBlackPaymentClientDTOs.FactItem> result = new ArrayList<>();
        for (int offset = 0; offset < orders.size(); offset += INTERNAL_BATCH_SIZE) {
            int end = Math.min(offset + INTERNAL_BATCH_SIZE, orders.size());
            TradeBlackPaymentClientDTOs.ResolveCommand command = new TradeBlackPaymentClientDTOs.ResolveCommand();
            command.setReferences(orders.subList(offset, end).stream().map(this::toClientReference).toList());
            TradeBlackPaymentClientDTOs.FactsResult facts = paymentInternalClient.resolveTradeBlackTransactions(command);
            if (facts != null && facts.getItems() != null) {
                result.addAll(facts.getItems());
            }
        }
        return result;
    }

    private TradeBlackPaymentClientDTOs.Reference toClientReference(TradeBlackWorkflowDTOs.OrderReference source) {
        TradeBlackPaymentClientDTOs.Reference target = new TradeBlackPaymentClientDTOs.Reference();
        target.setRowNo(source.getRowNo());
        target.setIdentifierType(upper(source.getIdentifierType()));
        target.setLookupMerchantId(trim(source.getLookupMerchantId()));
        target.setOrderNo(trim(source.getOrderNo()));
        return target;
    }

    private void validate(TradeBlackWorkflowDTOs.PreviewRequest request) {
        if (request == null || request.getOrders() == null || request.getOrders().isEmpty()) {
            throw invalid("请输入交易订单号");
        }
        String inputMode = upper(request.getInputMode());
        if (!Set.of("SINGLE", "PASTE", "FILE").contains(inputMode)) {
            throw invalid("录入方式不正确");
        }
        int limit = "FILE".equals(inputMode)
                ? TradeBlackWorkflowDTOs.MAX_FILE_ORDERS : TradeBlackWorkflowDTOs.MAX_MANUAL_ORDERS;
        if (request.getOrders().size() > limit) {
            throw invalid("当前录入方式最多处理" + limit + "笔交易");
        }
        String scopeType = upper(request.getScopeType());
        if (!Set.of(GLOBAL, MERCHANT).contains(scopeType)) {
            throw invalid("黑名单作用域不正确");
        }
        List<String> merchants = targetMerchantIds(request);
        if (MERCHANT.equals(scopeType) && merchants.isEmpty()) {
            throw invalid("商户作用域至少选择一个目标商户");
        }
        if (merchants.size() > MAX_TARGET_MERCHANTS) {
            throw invalid("一次最多选择20个目标商户");
        }
        if (!Set.of(ADD, RELEASE).contains(upper(request.getActionType()))) {
            throw invalid("操作类型不正确");
        }
        ensureSystemTradePermission(request.getInputMode(), request.getActionType());
        for (String functionCode : distinctFunctions(request)) {
            RiskFunctionDefinition.require("BLACK", functionCode);
        }
        if (distinctFunctions(request).isEmpty()) {
            throw invalid("请至少选择一个黑名单功能");
        }
        int index = 0;
        for (TradeBlackWorkflowDTOs.OrderReference order : request.getOrders()) {
            index++;
            if (order.getRowNo() == null) {
                order.setRowNo(index);
            }
            String type = upper(order.getIdentifierType());
            if (!Set.of("MERCHANT_ORDER_NO", "SYSTEM_ORDER_NO").contains(type)) {
                throw invalid("第" + order.getRowNo() + "行订单号类型不正确");
            }
            if ("MERCHANT_ORDER_NO".equals(type) && !StringUtils.hasText(order.getLookupMerchantId())) {
                throw invalid("第" + order.getRowNo() + "行按商户订单号查询时必须选择来源商户");
            }
            if (!StringUtils.hasText(order.getOrderNo())) {
                throw invalid("第" + order.getRowNo() + "行订单号不能为空");
            }
        }
    }

    private void ensureSystemTradePermission(String inputMode, String actionType) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        List<String> permissions = account.getPermissions();
        if (permissions.contains("*:*:*")) {
            return;
        }
        if (ADD.equals(upper(actionType))) {
            boolean allowed = permissions.contains("risk:tradeBlack:system:add")
                    || ("FILE".equals(upper(inputMode))
                    && permissions.contains("risk:tradeBlack:system:batchAdd"));
            if (allowed) {
                return;
            }
        } else if (permissions.contains("risk:tradeBlack:system:release")) {
            return;
        }
        throw new ServiceException(ApiResultEnum.FORBIDDEN);
    }

    private List<String> distinctFunctions(TradeBlackWorkflowDTOs.PreviewRequest request) {
        if (request.getFunctionCodes() == null) {
            return List.of();
        }
        return request.getFunctionCodes().stream().map(this::trim).filter(StringUtils::hasText).distinct().toList();
    }

    private List<TargetScope> targetScopes(TradeBlackWorkflowDTOs.PreviewRequest request) {
        if (GLOBAL.equals(upper(request.getScopeType()))) {
            return List.of(new TargetScope(GLOBAL, ""));
        }
        return targetMerchantIds(request).stream().map(id -> new TargetScope(MERCHANT, id)).toList();
    }

    private List<String> targetMerchantIds(TradeBlackWorkflowDTOs.PreviewRequest request) {
        if (request.getMerchantIds() == null) {
            return List.of();
        }
        return request.getMerchantIds().stream().map(this::trim).filter(StringUtils::hasText).distinct().toList();
    }

    private String countryAlpha2(String country) {
        if (!StringUtils.hasText(country)) {
            throw invalid("交易国家/地区为空");
        }
        return IsoCountryResolver.resolve(country.trim())
                .map(value -> value.alpha2().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> invalid("无法识别交易国家/地区"));
    }

    private String candidateKey(TradeBlackPaymentClientDTOs.FactItem fact,
                                String functionCode,
                                String sourceField,
                                TargetScope scope,
                                String matchKey) {
        return sha256(String.join("|",
                value(fact.getTransactionId()), functionCode, value(sourceField), scope.scope(),
                value(scope.merchantId()), value(matchKey)));
    }

    private void insertLegacyAudit(String batchNo,
                                   TradeBlackWorkflowDTOs.PreviewRequest workflow,
                                   CandidatePlan candidate,
                                   String resultCode) {
        Map<String, Object> data = new LinkedHashMap<>();
        TradeBlackWorkflowDTOs.Candidate view = candidate.view();
        data.put("merchantId", value(view.getTargetMerchantId()));
        data.put("merchantName", riskManagementMapper.selectMerchantName(view.getTargetMerchantId()));
        data.put("merchantOrderNo", view.getMerchantOrderNo());
        data.put("paymentOrderNo", view.getTransactionId());
        data.put("blackTargetType", view.getFunctionCode());
        data.put("blackTargetValueMasked", view.getMaskedValue());
        data.put("blackTargetHash", candidate.plan().matchKey());
        data.put("sourceType", upper(workflow.getInputMode()));
        data.put("actionType", upper(workflow.getActionType()));
        data.put("actionReason", trim(workflow.getActionReason()));
        data.put("batchNo", batchNo);
        data.put("candidateKey", view.getCandidateKey());
        data.put("status", "ADDED".equals(resultCode) ? 1 : 0);
        riskManagementMapper.insertTradeBlack(data, currentOperatorName());
    }

    private void insertBatchItem(String batchNo,
                                 TradeBlackWorkflowDTOs.Candidate candidate,
                                 String resultCode,
                                 String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchNo", batchNo);
        data.put("candidateKey", candidate.getCandidateKey());
        data.put("rowNo", candidate.getRowNo());
        data.put("inputOrderNo", candidate.getInputOrderNo());
        data.put("transactionId", candidate.getTransactionId());
        data.put("sourceMerchantId", candidate.getSourceMerchantId());
        data.put("merchantOrderNo", candidate.getMerchantOrderNo());
        data.put("functionCode", candidate.getFunctionCode());
        data.put("functionName", candidate.getFunctionName());
        data.put("sourceField", candidate.getSourceField());
        data.put("targetScope", candidate.getTargetScope());
        data.put("targetMerchantId", value(candidate.getTargetMerchantId()));
        data.put("maskedValue", candidate.getMaskedValue());
        data.put("resultCode", resultCode);
        data.put("resultMessage", message);
        workflowMapper.insertItem(data, currentOperatorName());
    }

    private TradeBlackWorkflowDTOs.ExecuteItem toExecuteItem(TradeBlackWorkflowDTOs.Candidate candidate,
                                                             String code,
                                                             String message) {
        TradeBlackWorkflowDTOs.ExecuteItem item = new TradeBlackWorkflowDTOs.ExecuteItem();
        item.setCandidateKey(candidate.getCandidateKey());
        item.setRowNo(candidate.getRowNo());
        item.setOrderNo(candidate.getInputOrderNo());
        item.setFunctionCode(candidate.getFunctionCode());
        item.setFunctionName(candidate.getFunctionName());
        item.setSourceField(candidate.getSourceField());
        item.setTargetScope(candidate.getTargetScope());
        item.setTargetMerchantId(candidate.getTargetMerchantId());
        item.setMaskedValue(candidate.getMaskedValue());
        item.setResultCode(code);
        item.setMessage(message);
        return item;
    }

    private TradeBlackWorkflowDTOs.ExecuteResponse existingBatch(Map<String, Object> row,
                                                                  String requestFingerprint) {
        String existingFingerprint = value(row.get("request_fingerprint"));
        if (StringUtils.hasText(existingFingerprint) && !existingFingerprint.equals(requestFingerprint)) {
            throw invalid("请求幂等号已用于其他交易加黑操作，请重新生成预览后再试");
        }
        TradeBlackWorkflowDTOs.ExecuteResponse response = new TradeBlackWorkflowDTOs.ExecuteResponse();
        response.setBatchNo(value(row.get("batch_no")));
        response.setStatus(value(row.get("status")));
        response.setTotalCount(intValue(row.get("total_count")));
        response.setSuccessCount(intValue(row.get("success_count")));
        response.setSkippedCount(intValue(row.get("skipped_count")));
        response.setFailureCount(intValue(row.get("failure_count")));
        return response;
    }

    /** 生成不包含敏感明文的稳定请求摘要。 */
    private String requestFingerprint(TradeBlackWorkflowDTOs.ExecuteRequest request, Set<String> selectedKeys) {
        TradeBlackWorkflowDTOs.PreviewRequest workflow = request.getWorkflow();
        List<String> normalizedOrders = workflow.getOrders().stream()
                .map(order -> String.join("~",
                        value(upper(order.getIdentifierType())),
                        value(trim(order.getLookupMerchantId())),
                        value(trim(order.getOrderNo()))))
                .sorted()
                .toList();
        String canonical = String.join("|",
                value(upper(workflow.getInputMode())),
                value(upper(workflow.getScopeType())),
                value(upper(workflow.getActionType())),
                value(trim(workflow.getActionReason())),
                String.join(",", targetMerchantIds(workflow).stream().sorted().toList()),
                String.join(",", distinctFunctions(workflow).stream().sorted().toList()),
                String.join(",", normalizedOrders),
                String.join(",", selectedKeys.stream().sorted().toList()));
        return sha256(canonical);
    }

    private TradeBlackWorkflowDTOs.OrderError orderError(TradeBlackPaymentClientDTOs.FactItem fact) {
        TradeBlackWorkflowDTOs.OrderError error = new TradeBlackWorkflowDTOs.OrderError();
        error.setRowNo(fact.getRowNo());
        error.setOrderNo(fact.getInputOrderNo());
        error.setCode(fact.getErrorCode());
        error.setMessage(fact.getErrorMessage());
        return error;
    }

    private String resultMessage(String code) {
        return switch (code) {
            case "ADDED" -> "已加入黑名单";
            case "RELEASED" -> "已解除黑名单";
            case "ALREADY_EXISTS" -> "黑名单已存在，幂等跳过";
            case "ALREADY_MISSING" -> "黑名单不存在，幂等跳过";
            default -> code;
        };
    }

    private String stateMessage(String state) {
        return switch (state) {
            case "READY" -> "可执行";
            case "ALREADY_EXISTS" -> "当前作用域已存在";
            case "ALREADY_MISSING" -> "当前作用域不存在";
            default -> state;
        };
    }

    private String safeFailureMessage(Exception exception) {
        if (!(exception instanceof ServiceException)) {
            return "执行失败，请稍后重试";
        }
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return "执行失败";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        if (StringUtils.hasText(account.getLoginAccount())) {
            return account.getLoginAccount();
        }
        return String.valueOf(account.getAccountId());
    }

    private Long currentOperatorAccountId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return account.getAccountId();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "候选项标识生成失败");
        }
    }

    private String fullName(TradeBlackPaymentClientDTOs.PersonAddress address) {
        if (address == null) {
            return null;
        }
        return String.join(" ", value(address.getFirstName()), value(address.getLastName())).trim();
    }

    private String value(TradeBlackPaymentClientDTOs.PersonAddress address, ContactField field) {
        if (address == null) {
            return null;
        }
        return field == ContactField.EMAIL ? address.getEmail() : address.getPhone();
    }

    private String value(TradeBlackPaymentClientDTOs.PersonAddress address, AddressField field) {
        if (address == null) {
            return null;
        }
        return switch (field) {
            case STREET -> address.getStreet();
            case POSTAL -> address.getPostal();
            case COUNTRY -> address.getCountry();
        };
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    private enum ContactField {
        EMAIL("email", "邮箱"), PHONE("phone", "电话");

        private final String fieldName;
        private final String label;

        ContactField(String fieldName, String label) {
            this.fieldName = fieldName;
            this.label = label;
        }

        public String fieldName() {
            return fieldName;
        }

        public String label() {
            return label;
        }
    }

    private enum EmailPart {
        USERNAME,
        DOMAIN
    }

    private enum AddressField { STREET, POSTAL, COUNTRY }

    private record TargetScope(String scope, String merchantId) {
    }

    private record RegionValue(String country, String state, String city, String level) {
    }

    private record RawCandidate(String functionCode,
                                String sourceField,
                                String sourceLabel,
                                String rawValue,
                                String trustedMasked,
                                String trustedHash,
                                String trustedCardBrand,
                                RegionValue region) {
    }

    private record CandidatePlan(TradeBlackWorkflowDTOs.Candidate view,
                                 AdminRiskManagementApplicationService.DerivedListPlan plan) {
    }

    private record PreparedPreview(TradeBlackWorkflowDTOs.PreviewResponse response,
                                   List<CandidatePlan> plans) {
    }
}
