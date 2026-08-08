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
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentChannelRouteService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道路由默认实现，位于 service-payment 服务实现层，按商户 MID 绑定和 MID 能力范围选择渠道，当前不做权重和复杂路由。
 * @status : create
 */
@Service
@Slf4j
public class DefaultPaymentChannelRouteService implements PaymentChannelRouteService {

    /**
     * NOT DELETED，用于保存 Default Payment Channel Route Service 中与 notdeleted 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long NOT_DELETED = 0L;

    /**
     * ENABLED，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int ENABLED = 1;

    /**
     * BUSINESS ACQUIRING，用于保存 Default Payment Channel Route Service 中与 businessacquiring 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String BUSINESS_ACQUIRING = "ACQUIRING";

    /**
     * ALL，用于保存 Default Payment Channel Route Service 中与 all 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String ALL = "ALL";

    private static final String SCOPE_SEPARATOR = ",";

    /**
     * DEFAULT PAYMENT METHOD，表示支付方式、通知方式或调用方式。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_PAYMENT_METHOD = "BANK_CARD";

    /**
     * MID Config Mapper，用于定位渠道商户号配置或渠道侧 MID。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final PaymentChannelMidConfigMapper midConfigMapper;

    /**
     * channel Info Mapper 依赖，用于 Default Payment Channel Route Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final PaymentChannelInfoMapper channelInfoMapper;

    /** 商户非敏感路由永久快照查询服务。 */
    private final MerchantRouteProfileCacheService routeProfileCacheService;

    /** 仅驻留当前 JVM 的短时渠道敏感元数据缓存。 */
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
        log.info("event: PAYMENT_ROUTE_START stage=ROUTE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} payerCountry: {}",
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
        log.info("event: PAYMENT_ROUTE_CANDIDATES stage=ROUTE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} bindingCount: {} candidateCount: {} candidates: {}",
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
            log.warn("event: PAYMENT_ROUTE_NO_CANDIDATE stage=ROUTE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} bindingCount: {} candidateCount: {} durationMs: {}",
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
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户未配置可用渠道MID");
        }
        if (candidates.size() > 1) {
            log.warn("event: PAYMENT_ROUTE_MULTI_CANDIDATE stage=ROUTE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} candidateCount: {} candidates: {} durationMs: {}",
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
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户渠道MID配置命中多条，请先收敛绑定关系");
        }
        RouteCandidate candidate = candidates.get(0);
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
        resultDTO.setSupportedCurrencies(candidate.supportedCurrencies());
        resultDTO.setRouteReason("MERCHANT_MID_BINDING");
        log.info("event: PAYMENT_ROUTE_END stage=ROUTE traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} channelCode: {} channelId: {} midConfigId: {} midNo: {} capabilityId: {} supportedCurrencies: {} requestedCurrency: {} routedCurrency: {} edcRequired: {} endpointHost: {} connectTimeoutSeconds: {} readTimeoutSeconds: {} routeReason: {} durationMs: {}",
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
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "channelCode and midConfigId are required");
        }
        ChannelMidConfigDO midConfig = midConfigMapper.selectById(midConfigId);
        if (midConfig == null || NOT_DELETED != safeDeleted(midConfig.getDeleted())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "channel MID config is not available");
        }
        Long resolvedChannelId = channelId == null ? midConfig.getChannelId() : channelId;
        ChannelInfoDO channelInfo = resolvedChannelId == null ? null : channelInfoMapper.selectById(resolvedChannelId);
        if (channelInfo == null || NOT_DELETED != safeDeleted(channelInfo.getDeleted())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "channel info is not available");
        }
        String restoredChannelCode = firstText(channelInfo.getChannelCode(), midConfig.getChannelCode(), normalizedChannelCode);
        if (!normalizedChannelCode.equals(normalize(restoredChannelCode))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "channel config does not match operation channel");
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
        log.info("event: PAYMENT_ROUTE_RESTORED stage=ROUTE traceId: {} channelCode: {} channelId: {} midConfigId: {} resolvedChannelCode: {} midNo: {} routeReason: {} durationMs: {}",
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
                && !matchesScope(option.getCardBrandScope(), cardBrand)) {
            return null;
        }
        if (!matchesScope(option.getTransactionTypeScope(), commandDTO.getTransactionType())) {
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
        String routedCurrency = directCurrencySupported ? requestedCurrency : normalize(currencies.get(0));
        return new RouteCandidate(
                toChannelInfo(option),
                toMidConfig(option),
                toCapability(option),
                new ArrayList<>(currencies),
                routedCurrency,
                !directCurrencySupported
        );
    }

    /** 将缓存候选适配为既有路由内部渠道对象。 */
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

    /** 将缓存候选适配为既有路由内部 MID 对象，不填充敏感元数据正文。 */
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

    /** 将缓存候选适配为既有路由内部能力对象。 */
    private ChannelPaymentCapabilityDO toCapability(RouteOption option) {
        ChannelPaymentCapabilityDO capability = new ChannelPaymentCapabilityDO();
        capability.setId(option.getCapabilityId());
        capability.setChannelId(option.getChannelId());
        capability.setChannelCode(option.getChannelCode());
        capability.setBusinessType(option.getCapabilityBusinessType());
        capability.setPaymentMethod(option.getCapabilityPaymentMethod());
        capability.setTransactionType(option.getCapabilityTransactionType());
        capability.setCapabilityStatus(option.getCapabilityStatus());
        capability.setSortOrder(option.getCapabilitySortOrder());
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
        List<Map<String, Object>> rows = candidates.stream()
                .map(candidate -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("channelCode", candidate.channelInfo().getChannelCode());
                    row.put("channelId", candidate.channelInfo().getId());
                    row.put("midConfigId", candidate.midConfig().getId());
                    row.put("midNo", maskMidNo(candidate.midConfig().getChannelMid()));
                    row.put("capabilityId", candidate.capability().getId());
                    row.put("capabilityTransactionType", candidate.capability().getTransactionType());
                    row.put("supportedCurrencies", candidate.supportedCurrencies());
                    row.put("routedCurrency", candidate.routedCurrency());
                    row.put("edcRequired", candidate.edcRequired());
                    row.put("endpointHost", endpointHost(candidate.channelInfo().getDefaultRequestUrl()));
                    row.put("connectTimeoutSeconds", candidate.channelInfo().getConnectTimeoutSeconds());
                    row.put("readTimeoutSeconds", candidate.channelInfo().getReadTimeoutSeconds());
                    return row;
                })
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
     * 规范化matchesscope，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param scope scope 输入值，参与 scope 的查询、校验、转换、写入或日志摘要
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 整理matches交易type，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param capabilityTransactionType capability Transaction Type 输入值，参与 capability交易type 的查询、校验、转换、写入或日志摘要
     * @param requestedTransactionType requested Transaction Type 输入值，参与 requested交易type 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private boolean matchesTransactionType(String capabilityTransactionType, String requestedTransactionType) {
        return matchesScope(capabilityTransactionType, requestedTransactionType);
    }

    /**
     * 解析normalize，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
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
     * 解析resolvepaymentmethod，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param commandDTO command DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolvePaymentMethod(PaymentCreateCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getPaymentMethod()) ? normalize(commandDTO.getPaymentMethod()) : DEFAULT_PAYMENT_METHOD;
    }

    /**
     * 整理耗时毫秒数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param startNanos start Nanos 输入值，参与 startnanos 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 解析parsemetadata，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param metadataValueJson metadata Value Json 输入值，参与 metadata值json 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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

    private record RouteCandidate(ChannelInfoDO channelInfo,
                                  ChannelMidConfigDO midConfig,
                                  ChannelPaymentCapabilityDO capability,
                                  List<String> supportedCurrencies,
                                  String routedCurrency,
                                  boolean edcRequired) {
    }
}
