package com.scott.payment.payment.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.component.db.route.model.MerchantRouteProfile.RouteOption;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.entity.ChannelInfoDO;
import com.scott.payment.payment.entity.ChannelMidConfigDO;
import com.scott.payment.payment.entity.ChannelPaymentCapabilityDO;
import com.scott.payment.payment.mapper.PaymentChannelInfoMapper;
import com.scott.payment.payment.mapper.PaymentChannelMidConfigMapper;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.MerchantRouteProfileCacheService;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 根据商户绑定、MID 范围和渠道能力选择收单路由。
 *
 * <p>多个候选同时满足硬性约束时，原币直连优先于 EDC，再使用能力配置中的
 * {@code capabilitySortOrder}。最高优先级仍重复时拒绝交易，禁止依赖集合顺序随机选择资金通道。</p>
 */
@Service
@Slf4j
public class DefaultPaymentChannelRouteService implements PaymentChannelRouteService {

    /**
     * 逻辑删除字段的有效值。
     */
    private static final long NOT_DELETED = 0L;

    /**
     * 配置、渠道和能力表统一使用的启用值。
     */
    private static final int ENABLED = 1;

    /**
     * 本服务只消费收单业务路由。
     */
    private static final String BUSINESS_ACQUIRING = "ACQUIRING";

    /**
     * 范围配置不限制具体值时使用的通配字面量。
     */
    private static final String ALL = "ALL";

    /**
     * 数据库存储多值范围时使用的分隔符。
     */
    private static final String SCOPE_SEPARATOR = ",";

    /**
     * 历史命令未显式传支付方式时兼容为银行卡。
     */
    private static final String DEFAULT_PAYMENT_METHOD = "BANK_CARD";

    private final PaymentChannelMidConfigMapper midConfigMapper;

    private final PaymentChannelInfoMapper channelInfoMapper;

    /**
     * 商户非敏感路由永久快照查询服务。
     */
    private final MerchantRouteProfileCacheService routeProfileCacheService;

    /**
     * 仅驻留当前 JVM 的短时渠道敏感元数据缓存。
     */
    private final PaymentChannelMidMetadataCache midMetadataCache;

    /**
     * 创建渠道路由服务。
     *
     * @param midConfigMapper  MID 配置 Mapper
     * @param channelInfoMapper 渠道基础信息 Mapper
     * @param routeProfileCacheService 商户路由永久快照查询服务
     * @param midMetadataCache 渠道敏感元数据本地缓存
     */
    public DefaultPaymentChannelRouteService(PaymentChannelMidConfigMapper midConfigMapper,
                                             PaymentChannelInfoMapper channelInfoMapper,
                                             MerchantRouteProfileCacheService routeProfileCacheService,
                                             PaymentChannelMidMetadataCache midMetadataCache) {
        this.midConfigMapper = midConfigMapper;
        this.channelInfoMapper = channelInfoMapper;
        this.routeProfileCacheService = routeProfileCacheService;
        this.midMetadataCache = midMetadataCache;
    }

    /**
     * 根据商户、支付方式、交易类型、币种和国家选择可用渠道 MID。
     *
     * @param commandDTO 创建交易命令
     * @return 渠道路由结果
     */
    @Override
    public PaymentRouteResultDTO route(PaymentCreateCommandDTO commandDTO) {
        long startNanos = System.nanoTime();
        log.info("收单渠道路由开始, event: PAYMENT_ROUTE_START, stage: ROUTE, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, transactionType: {}, paymentMethod: {}, currency: {}, amount: {}, payerCountry: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getTransactionId(),
                commandDTO.getTransactionType(),
                resolvePaymentMethod(commandDTO),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                commandDTO.getBillingCardHolderInfo() == null ? null : commandDTO.getBillingCardHolderInfo().getCountry());
        MerchantRouteProfile routeProfile = routeProfileCacheService.findRouteProfile(commandDTO.getMerchantId());
        List<RouteOption> routeOptions = routeProfile == null
                ? List.of()
                : routeProfile.getRouteOptions();
        int bindingCount = routeProfile == null || routeProfile.getBindingCount() == null
                ? 0
                : routeProfile.getBindingCount();
        LocalDateTime now = LocalDateTime.now();
        List<RouteCandidate> candidates = routeOptions.stream()
                .map(option -> toCandidate(option, commandDTO, now))
                .filter(candidate -> candidate != null)
                .toList();
        log.info("收单渠道候选已完成筛选, event: PAYMENT_ROUTE_CANDIDATES, stage: ROUTE, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, transactionType: {}, paymentMethod: {}, currency: {}, amount: {}, bindingCount: {}, candidateCount: {}, candidates: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getTransactionId(),
                commandDTO.getTransactionType(),
                resolvePaymentMethod(commandDTO),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                bindingCount,
                candidates.size(),
                candidateSummary(candidates));
        if (candidates.isEmpty()) {
            log.warn("收单渠道无可用候选, event: PAYMENT_ROUTE_NO_CANDIDATE, stage: ROUTE, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, transactionType: {}, paymentMethod: {}, currency: {}, amount: {}, bindingCount: {}, candidateCount: {}, durationMs: {}",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    commandDTO.getTransactionId(),
                    commandDTO.getTransactionType(),
                    resolvePaymentMethod(commandDTO),
                    commandDTO.getCurrency(),
                    commandDTO.getAmount(),
                    bindingCount,
                    candidates.size(),
                    elapsedMillis(startNanos));
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        RouteCandidate candidate = selectCandidate(candidates);
        if (candidate == null) {
            log.warn("收单渠道最高优先级候选不唯一, event: PAYMENT_ROUTE_MULTI_CANDIDATE, stage: ROUTE, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, transactionType: {}, paymentMethod: {}, currency: {}, amount: {}, candidateCount: {}, candidates: {}, durationMs: {}",
                    TraceContext.getTraceId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    commandDTO.getTransactionId(),
                    commandDTO.getTransactionType(),
                    resolvePaymentMethod(commandDTO),
                    commandDTO.getCurrency(),
                    commandDTO.getAmount(),
                    candidates.size(),
                    candidateSummary(candidates),
                    elapsedMillis(startNanos));
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        PaymentRouteResultDTO resultDTO = PaymentRouteResultDTO.routed(candidate.channelInfo().getChannelCode());
        resultDTO.setChannelId(candidate.channelInfo().getId());
        resultDTO.setMidConfigId(candidate.midConfig().getId());
        resultDTO.setMidNo(candidate.midConfig().getChannelMid());
        resultDTO.setRequestUrl(candidate.channelInfo().getDefaultRequestUrl());
        resultDTO.setConnectTimeoutSeconds(candidate.channelInfo().getConnectTimeoutSeconds());
        resultDTO.setReadTimeoutSeconds(candidate.channelInfo().getReadTimeoutSeconds());
        resultDTO.setMetadataValues(parseMetadata(midMetadataCache.getMetadataJson(
                candidate.midConfig().getId(),
                candidate.midConfig().getUpdateTime()
        )));
        resultDTO.setRequestedCurrency(normalize(commandDTO.getCurrency()));
        resultDTO.setRoutedCurrency(candidate.routedCurrency());
        resultDTO.setEdcRequired(candidate.edcRequired());
        resultDTO.setCapabilityId(candidate.capability().getId());
        resultDTO.setThreeDsSupported(Integer.valueOf(ENABLED).equals(candidate.capability().getSupport3ds()));
        resultDTO.setSupportedCurrencies(candidate.supportedCurrencies());
        resultDTO.setRouteReason("MERCHANT_MID_BINDING");
        log.info("收单渠道路由完成, event: PAYMENT_ROUTE_END, stage: ROUTE, traceId: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, transactionType: {}, paymentMethod: {}, currency: {}, amount: {}, channelCode: {}, channelId: {}, midConfigId: {}, midNo: {}, capabilityId: {}, supportedCurrencies: {}, requestedCurrency: {}, routedCurrency: {}, edcRequired: {}, endpointHost: {}, connectTimeoutSeconds: {}, readTimeoutSeconds: {}, routeReason: {}, durationMs: {}",
                TraceContext.getTraceId(),
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getTransactionId(),
                commandDTO.getTransactionType(),
                resolvePaymentMethod(commandDTO),
                commandDTO.getCurrency(),
                commandDTO.getAmount(),
                resultDTO.getChannelCode(),
                resultDTO.getChannelId(),
                resultDTO.getMidConfigId(),
                maskMidNo(resultDTO.getMidNo()),
                resultDTO.getCapabilityId(),
                resultDTO.getSupportedCurrencies(),
                resultDTO.getRequestedCurrency(),
                resultDTO.getRoutedCurrency(),
                resultDTO.isEdcRequired(),
                endpointHost(resultDTO.getRequestUrl()),
                resultDTO.getConnectTimeoutSeconds(),
                resultDTO.getReadTimeoutSeconds(),
                resultDTO.getRouteReason(),
                elapsedMillis(startNanos));
        return resultDTO;
    }

    /**
     * 按原币直连和能力顺序选择唯一最高优先级候选。
     *
     * <p>没有配置顺序的候选只能在其本层唯一时被选择；两条候选的直连属性与最小
     * {@code capabilitySortOrder} 均相同时返回 {@code null}，由调用方 fail closed。</p>
     *
     * @param candidates 已通过全部硬性能力校验的候选
     * @return 唯一最高优先级候选；存在同优先级歧义时返回 {@code null}
     */
    private RouteCandidate selectCandidate(List<RouteCandidate> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        boolean directCandidateExists = candidates.stream().anyMatch(candidate -> !candidate.edcRequired());
        List<RouteCandidate> currencyPreferred = directCandidateExists
                ? candidates.stream().filter(candidate -> !candidate.edcRequired()).toList()
                : candidates;
        if (currencyPreferred.size() == 1) {
            return currencyPreferred.get(0);
        }

        Integer minimumSortOrder = currencyPreferred.stream()
                .map(candidate -> candidate.capability().getSortOrder())
                .filter(sortOrder -> sortOrder != null)
                .min(Integer::compareTo)
                .orElse(null);
        if (minimumSortOrder == null) {
            return null;
        }
        List<RouteCandidate> priorityPreferred = currencyPreferred.stream()
                .filter(candidate -> minimumSortOrder.equals(candidate.capability().getSortOrder()))
                .toList();
        return priorityPreferred.size() == 1 ? priorityPreferred.get(0) : null;
    }

    /**
     * 按交易动作单已保存的渠道和 MID 配置恢复渠道调用参数。
     * <p>
     * 查询勾兑必须使用原动作单的 channel_mid_config_id 和 channel_transaction_id，
     * 不能重新执行商户路由，否则绑定调整后可能查错渠道或查不到原交易。
     *
     * @param channelCode 渠道编码
     * @param channelId 渠道信息 ID
     * @param midConfigId MID 配置 ID
     * @param fallbackMidNo 历史动作单保存的 MID 或终端号
     * @return 渠道路由快照
     */
    @Override
    public PaymentRouteResultDTO restore(String channelCode, Long channelId, Long midConfigId, String fallbackMidNo) {
        long startNanos = System.nanoTime();
        String normalizedChannelCode = normalize(channelCode);
        if (!StringUtils.hasText(normalizedChannelCode) || midConfigId == null) {
            rejectOriginalTransaction("original channel identity is incomplete", channelCode, channelId, midConfigId);
        }
        ChannelMidConfigDO midConfig = midConfigMapper.selectById(midConfigId);
        LocalDateTime now = LocalDateTime.now();
        if (midConfig == null
                || NOT_DELETED != safeDeleted(midConfig.getDeleted())
                || !Integer.valueOf(ENABLED).equals(midConfig.getMidStatus())
                || !isActive(now, midConfig.getEffectiveTime(), midConfig.getExpireTime())) {
            rejectOriginalTransaction("original channel MID is unavailable", channelCode, channelId, midConfigId);
        }
        Long resolvedChannelId = channelId == null ? midConfig.getChannelId() : channelId;
        ChannelInfoDO channelInfo = resolvedChannelId == null ? null : channelInfoMapper.selectById(resolvedChannelId);
        if (channelInfo == null
                || NOT_DELETED != safeDeleted(channelInfo.getDeleted())
                || !Integer.valueOf(ENABLED).equals(channelInfo.getChannelStatus())
                || !Integer.valueOf(ENABLED).equals(channelInfo.getSupportAcquiring())) {
            rejectOriginalTransaction("original channel is unavailable", channelCode, resolvedChannelId, midConfigId);
        }
        String restoredChannelCode = firstText(channelInfo.getChannelCode(), midConfig.getChannelCode(), normalizedChannelCode);
        if (!normalizedChannelCode.equals(normalize(restoredChannelCode))) {
            rejectOriginalTransaction("original channel identity does not match current configuration",
                    channelCode, resolvedChannelId, midConfigId);
        }
        PaymentRouteResultDTO resultDTO = PaymentRouteResultDTO.routed(restoredChannelCode);
        resultDTO.setChannelId(channelInfo.getId());
        resultDTO.setMidConfigId(midConfig.getId());
        resultDTO.setMidNo(firstText(midConfig.getChannelMid(), fallbackMidNo));
        resultDTO.setRequestUrl(channelInfo.getDefaultRequestUrl());
        resultDTO.setConnectTimeoutSeconds(channelInfo.getConnectTimeoutSeconds());
        resultDTO.setReadTimeoutSeconds(channelInfo.getReadTimeoutSeconds());
        resultDTO.setMetadataValues(parseMetadata(midConfig.getMetadataValueJson()));
        resultDTO.setRouteReason("RESTORED_FROM_TRANSACTION_OPERATION");
        log.info("原交易渠道路由已恢复, event: PAYMENT_ROUTE_RESTORED, stage: ROUTE, traceId: {}, channelCode: {}, channelId: {}, midConfigId: {}, resolvedChannelCode: {}, midNo: {}, routeReason: {}, durationMs: {}",
                TraceContext.getTraceId(),
                channelCode,
                channelId,
                midConfigId,
                resultDTO.getChannelCode(),
                maskMidNo(resultDTO.getMidNo()),
                resultDTO.getRouteReason(),
                elapsedMillis(startNanos));
        return resultDTO;
    }

    /**
     * 拒绝不可继续执行的原交易；具体配置原因只进入内部日志，对外保持稳定响应。
     */
    private void rejectOriginalTransaction(String internalReason,
                                           String channelCode,
                                           Long channelId,
                                           Long midConfigId) {
        log.warn("原交易渠道路由不可继续, event: ORIGINAL_TRANSACTION_ROUTE_REJECTED, stage: ROUTE, traceId: {}, channelCode: {}, channelId: {}, midConfigId: {}, reason: {}",
                TraceContext.getTraceId(), channelCode, channelId, midConfigId, internalReason);
        throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
    }

    /**
     * 将有效商户 MID 绑定转换为当前交易可用的路由候选。
     *
     * <p>依次校验 MID 与渠道启用状态、有效期、收单业务类型、支付方式、交易类型、
     * 国家、币种和金额范围；任一约束不匹配即返回 null。</p>
     *
     * @param option     永久缓存中的非敏感路由候选
     * @param commandDTO 支付创建命令
     * @param now        路由评估时间
     * @return 可用候选；不满足约束时返回 null
     */
    private RouteCandidate toCandidate(RouteOption option,
                                       PaymentCreateCommandDTO commandDTO,
                                       LocalDateTime now) {
        if (!Integer.valueOf(ENABLED).equals(option.getBindingStatus())
                || !isActive(now, option.getBindingEffectiveTime(), option.getBindingExpireTime())
                || !Integer.valueOf(ENABLED).equals(option.getMidStatus())
                || !isActive(now, option.getMidEffectiveTime(), option.getMidExpireTime())
                || !Integer.valueOf(ENABLED).equals(option.getChannelStatus())
                || !Integer.valueOf(ENABLED).equals(option.getSupportAcquiring())
                || !Integer.valueOf(ENABLED).equals(option.getCapabilityStatus())) {
            return null;
        }
        if (!BUSINESS_ACQUIRING.equals(normalize(option.getBusinessType()))
                || !BUSINESS_ACQUIRING.equals(normalize(option.getCapabilityBusinessType()))) {
            return null;
        }
        String paymentMethod = resolvePaymentMethod(commandDTO);
        if (!matchesScope(option.getPaymentMethodScope(), paymentMethod)
                || !normalize(paymentMethod).equals(normalize(option.getCapabilityPaymentMethod()))) {
            return null;
        }
        String cardBrand = commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getCardBrand();
        if ("BANK_CARD".equals(normalize(paymentMethod))
                && (!matchesScope(option.getCardBrandScope(), cardBrand)
                || !matchesCapabilityCardBrand(option.getCapabilitySupportedCardBrands(), cardBrand))) {
            return null;
        }
        if (!matchesScope(option.getTransactionTypeScope(), commandDTO.getTransactionType())) {
            return null;
        }
        if ("INCREMENTAL_AUTHORIZATION".equals(normalize(commandDTO.getTransactionType()))
                && !Integer.valueOf(ENABLED).equals(option.getCapabilitySupportIncrementalAuthorization())) {
            return null;
        }
        String payerCountry = commandDTO.getBillingCardHolderInfo() == null ? null : commandDTO.getBillingCardHolderInfo().getCountry();
        if (!matchesScope(option.getAllowedCountryScope(), payerCountry)
                || !matchesTransactionType(option.getCapabilityTransactionType(), commandDTO.getTransactionType())) {
            return null;
        }
        List<String> currencies = option.getSupportedCurrencies() == null
                ? List.of()
                : option.getSupportedCurrencies();
        if (currencies.isEmpty()) {
            return null;
        }
        String requestedCurrency = normalize(commandDTO.getCurrency());
        boolean directCurrencySupported = currencies.stream().anyMatch(item -> normalize(item).equals(requestedCurrency));
        String defaultTransactionCurrency = normalize(option.getDefaultTransactionCurrency());
        if (!directCurrencySupported && (defaultTransactionCurrency.isEmpty()
                || currencies.stream().noneMatch(item -> normalize(item).equals(defaultTransactionCurrency)))) {
            return null;
        }
        String routedCurrency = directCurrencySupported ? requestedCurrency : defaultTransactionCurrency;
        return new RouteCandidate(
                toChannelInfo(option),
                toMidConfig(option),
                toCapability(option),
                new ArrayList<>(currencies),
                routedCurrency,
                !directCurrencySupported
        );
    }

    /**
     * 银行卡路由必须命中能力表明确启用的卡品牌。
     */
    private boolean matchesCapabilityCardBrand(List<String> supportedCardBrands, String cardBrand) {
        if (supportedCardBrands == null || supportedCardBrands.isEmpty() || !StringUtils.hasText(cardBrand)) {
            return false;
        }
        String normalizedCardBrand = normalize(cardBrand);
        return supportedCardBrands.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .anyMatch(normalizedCardBrand::equals);
    }

    /**
     * 将缓存候选适配为既有路由内部渠道对象。
     */
    private ChannelInfoDO toChannelInfo(RouteOption option) {
        ChannelInfoDO channel = new ChannelInfoDO();
        channel.setId(option.getChannelId());
        channel.setChannelCode(option.getChannelCode());
        channel.setChannelStatus(option.getChannelStatus());
        channel.setSupportAcquiring(option.getSupportAcquiring());
        channel.setDefaultRequestUrl(option.getRequestUrl());
        channel.setConnectTimeoutSeconds(option.getConnectTimeoutSeconds());
        channel.setReadTimeoutSeconds(option.getReadTimeoutSeconds());
        channel.setDeleted(NOT_DELETED);
        return channel;
    }

    /**
     * 将缓存候选适配为既有路由内部 MID 对象，不填充敏感元数据正文。
     */
    private ChannelMidConfigDO toMidConfig(RouteOption option) {
        ChannelMidConfigDO mid = new ChannelMidConfigDO();
        mid.setId(option.getMidConfigId());
        mid.setChannelId(option.getChannelId());
        mid.setChannelCode(option.getChannelCode());
        mid.setChannelMid(option.getChannelMid());
        mid.setBusinessType(option.getBusinessType());
        mid.setPaymentMethodScope(option.getPaymentMethodScope());
        mid.setCardBrandScope(option.getCardBrandScope());
        mid.setTransactionTypeScope(option.getTransactionTypeScope());
        mid.setCurrencyScope(option.getCurrencyScope());
        mid.setAllowedCountryScope(option.getAllowedCountryScope());
        mid.setMidStatus(option.getMidStatus());
        mid.setEffectiveTime(option.getMidEffectiveTime());
        mid.setExpireTime(option.getMidExpireTime());
        mid.setUpdateTime(option.getMidModifiedTime());
        mid.setDeleted(NOT_DELETED);
        return mid;
    }

    /**
     * 将缓存候选适配为既有路由内部能力对象。
     */
    private ChannelPaymentCapabilityDO toCapability(RouteOption option) {
        ChannelPaymentCapabilityDO capability = new ChannelPaymentCapabilityDO();
        capability.setId(option.getCapabilityId());
        capability.setChannelId(option.getChannelId());
        capability.setChannelCode(option.getChannelCode());
        capability.setBusinessType(option.getCapabilityBusinessType());
        capability.setPaymentMethod(option.getCapabilityPaymentMethod());
        capability.setTransactionType(option.getCapabilityTransactionType());
        capability.setSupport3ds(option.getCapabilitySupport3ds());
        capability.setSupportIncrementalAuthorization(option.getCapabilitySupportIncrementalAuthorization());
        capability.setCapabilityStatus(option.getCapabilityStatus());
        capability.setSortOrder(option.getCapabilitySortOrder());
        capability.setDefaultTransactionCurrency(option.getDefaultTransactionCurrency());
        capability.setDeleted(NOT_DELETED);
        return capability;
    }

    /**
     * 生成候选渠道摘要。
     * <p>
     * 摘要用于排查商户 MID 绑定、渠道能力和币种匹配结果；MID 只输出掩码，不输出完整商户渠道号。
     * </p>
     * @param candidates 候选渠道列表
     * @return 候选摘要 JSON
     */
    private String candidateSummary(List<RouteCandidate> candidates) {
        List<RouteCandidateLogSummary> rows = candidates.stream()
                .map(candidate -> new RouteCandidateLogSummary(
                        candidate.channelInfo().getChannelCode(),
                        candidate.channelInfo().getId(),
                        candidate.midConfig().getId(),
                        maskMidNo(candidate.midConfig().getChannelMid()),
                        candidate.capability().getId(),
                        candidate.capability().getTransactionType(),
                        Integer.valueOf(ENABLED).equals(candidate.capability().getSupport3ds()),
                        candidate.supportedCurrencies(),
                        candidate.capability().getDefaultTransactionCurrency(),
                        candidate.routedCurrency(),
                        candidate.edcRequired(),
                        endpointHost(candidate.channelInfo().getDefaultRequestUrl()),
                        candidate.channelInfo().getConnectTimeoutSeconds(),
                        candidate.channelInfo().getReadTimeoutSeconds()))
                .toList();
        return JsonUtils.toJsonString(rows);
    }

    /**
     * 脱敏渠道 MID。
     *
     * @param midNo 渠道 MID 原文
     * @return 掩码 MID
     */
    private String maskMidNo(String midNo) {
        if (!StringUtils.hasText(midNo)) {
            return null;
        }
        String normalized = midNo.trim();
        if (normalized.length() <= 6) {
            return "***";
        }
        return normalized.substring(0, 3) + "***" + normalized.substring(normalized.length() - 3);
    }

    /**
     * 提取渠道请求地址主机名。
     *
     * @param url 渠道请求地址
     * @return 主机名或原始地址摘要
     */
    private String endpointHost(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return java.net.URI.create(url).getHost();
        } catch (RuntimeException exception) {
            return "invalid_url";
        }
    }

    /**
     * 判断渠道 MID 配置在当前时间是否可用于交易路由。
     * <p>
     * 生效时间为空表示立即可用，失效时间为空表示长期有效；该判断只负责时间窗口，不替代启停状态、
     * 币种、支付方式和交易类型能力校验。
     * </p>
     * @param now 当前路由评估时间
     * @param effectiveTime 配置生效时间
     * @param expireTime 配置失效时间
     * @return true 表示当前时间落在配置可用窗口内
     */
    private boolean isActive(LocalDateTime now, LocalDateTime effectiveTime, LocalDateTime expireTime) {
        return (effectiveTime == null || !now.isBefore(effectiveTime))
                && (expireTime == null || now.isBefore(expireTime));
    }

    /**
     * 判断渠道能力是否匹配请求范围。
     */
    private boolean matchesScope(String scope, String value) {
        if (!StringUtils.hasText(scope) || ALL.equalsIgnoreCase(scope.trim())) {
            return true;
        }
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalizedValue = normalize(value);
        for (String item : scope.split(SCOPE_SEPARATOR)) {
            if (normalizedValue.equals(normalize(item))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断渠道能力是否匹配交易类型。
     */
    private boolean matchesTransactionType(String capabilityTransactionType, String requestedTransactionType) {
        return matchesScope(capabilityTransactionType, requestedTransactionType);
    }

    /**
     * 规范化渠道能力匹配值。
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 兼容历史记录中 deleted 为空的情况。
     *
     * @param deleted 删除标识
     * @return 可参与比较的删除标识
     */
    private long safeDeleted(Long deleted) {
        return deleted == null ? NOT_DELETED : deleted;
    }

    /**
     * 取第一个非空文本，用于恢复路由时优先使用当前配置，缺失时兼容动作单快照。
     *
     * @param values 候选文本
     * @return 第一个非空文本
     */
    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 解析路由使用的支付方式，商户未指定时使用银行卡默认值。
     *
     * @param commandDTO 支付创建命令
     * @return 规范化后的支付方式编码
     */
    private String resolvePaymentMethod(PaymentCreateCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getPaymentMethod()) ? normalize(commandDTO.getPaymentMethod()) : DEFAULT_PAYMENT_METHOD;
    }

    /**
     * 计算从单调时钟起点开始的耗时毫秒数。
     *
     * @param startNanos 开始时的单调时钟纳秒值
     * @return 已经过的毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 解析渠道 MID 的动态路由扩展元数据。
     *
     * <p>元数据键由渠道配置动态扩展，因此此处保留字符串 Map；固定路由字段由强类型对象承载。</p>
     *
     * @param metadataValueJson 渠道 MID 元数据 JSON
     * @return 允许渠道适配器按键读取的字符串元数据
     */
    private Map<String, String> parseMetadata(String metadataValueJson) {
        if (!StringUtils.hasText(metadataValueJson)) {
            return Map.of();
        }
        Map<String, Object> values = JsonUtils.parseObject(metadataValueJson, new TypeReference<Map<String, Object>>() {
        });
        Map<String, String> result = new LinkedHashMap<>();
        if (values == null) {
            return result;
        }
        values.forEach((key, value) -> result.put(key, value == null ? null : String.valueOf(value)));
        return result;
    }

    /**
     * 封装渠道路由候选项及其排序依据。
     */
    private record RouteCandidate(ChannelInfoDO channelInfo,
                                  ChannelMidConfigDO midConfig,
                                  ChannelPaymentCapabilityDO capability,
                                  List<String> supportedCurrencies,
                                  String routedCurrency,
                                  boolean edcRequired) {
    }

    /**
     * 渠道路由候选的脱敏日志摘要。
     *
     * @param channelCode 渠道编码
     * @param channelId 渠道配置主键
     * @param midConfigId 渠道 MID 配置主键
     * @param midNo 渠道 MID 掩码
     * @param capabilityId 渠道能力主键
     * @param capabilityTransactionType 能力支持的交易类型
     * @param threeDsSupported 是否支持 3DS
     * @param supportedCurrencies 支持的交易币种
     * @param defaultTransactionCurrency 默认交易币种
     * @param routedCurrency 本次路由币种
     * @param edcRequired 是否需要动态换汇
     * @param endpointHost 渠道请求主机
     * @param connectTimeoutSeconds 连接超时秒数
     * @param readTimeoutSeconds 读取超时秒数
     */
    private record RouteCandidateLogSummary(String channelCode,
                                            Long channelId,
                                            Long midConfigId,
                                            String midNo,
                                            Long capabilityId,
                                            String capabilityTransactionType,
                                            boolean threeDsSupported,
                                            List<String> supportedCurrencies,
                                            String defaultTransactionCurrency,
                                            String routedCurrency,
                                            boolean edcRequired,
                                            String endpointHost,
                                            Integer connectTimeoutSeconds,
                                            Integer readTimeoutSeconds) {
    }
}
