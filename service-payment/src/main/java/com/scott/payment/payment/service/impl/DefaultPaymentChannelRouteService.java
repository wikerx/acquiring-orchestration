package com.scott.payment.payment.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.entity.ChannelCapabilityCurrencyDO;
import com.scott.payment.payment.entity.ChannelInfoDO;
import com.scott.payment.payment.entity.ChannelMidConfigDO;
import com.scott.payment.payment.entity.ChannelPaymentCapabilityDO;
import com.scott.payment.payment.entity.MerchantChannelMidBindingDO;
import com.scott.payment.payment.mapper.PaymentChannelCapabilityCurrencyMapper;
import com.scott.payment.payment.mapper.PaymentChannelInfoMapper;
import com.scott.payment.payment.mapper.PaymentChannelMidConfigMapper;
import com.scott.payment.payment.mapper.PaymentChannelPaymentCapabilityMapper;
import com.scott.payment.payment.mapper.PaymentMerchantChannelMidBindingMapper;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
     * NOT DELETED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final long NOT_DELETED = 0L;

    /**
     * ENABLED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int ENABLED = 1;

    /**
     * BUSINESS ACQUIRING 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String BUSINESS_ACQUIRING = "ACQUIRING";

    /**
     * ALL 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String ALL = "ALL";

    private static final String SCOPE_SEPARATOR = ",";

    /**
     * DEFAULT PAYMENT METHOD 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_PAYMENT_METHOD = "BANK_CARD";

    /**
     * mid Binding Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final PaymentMerchantChannelMidBindingMapper midBindingMapper;

    /**
     * mid Config Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final PaymentChannelMidConfigMapper midConfigMapper;

    /**
     * channel Info Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final PaymentChannelInfoMapper channelInfoMapper;

    /**
     * capability Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final PaymentChannelPaymentCapabilityMapper capabilityMapper;

    /**
     * capability Currency Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：ISO 4217 三位币种代码；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final PaymentChannelCapabilityCurrencyMapper capabilityCurrencyMapper;

    /**
     * 创建渠道路由服务。
     *
     * @param midBindingMapper 商户 MID 绑定 Mapper
     * @param midConfigMapper  MID 配置 Mapper
     * @param channelInfoMapper 渠道基础信息 Mapper
     * @param capabilityMapper 渠道支付能力 Mapper
     * @param capabilityCurrencyMapper 渠道能力币种 Mapper
     */
    public DefaultPaymentChannelRouteService(PaymentMerchantChannelMidBindingMapper midBindingMapper,
                                             PaymentChannelMidConfigMapper midConfigMapper,
                                             PaymentChannelInfoMapper channelInfoMapper,
                                             PaymentChannelPaymentCapabilityMapper capabilityMapper,
                                             PaymentChannelCapabilityCurrencyMapper capabilityCurrencyMapper) {
        this.midBindingMapper = midBindingMapper;
        this.midConfigMapper = midConfigMapper;
        this.channelInfoMapper = channelInfoMapper;
        this.capabilityMapper = capabilityMapper;
        this.capabilityCurrencyMapper = capabilityCurrencyMapper;
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
        log.info("event: PAYMENT_ROUTE_START merchantId: {} merchantOrderNo: {} transactionType: {} paymentMethod: {} currency: {} payerCountry: {}",
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getTransactionType(),
                resolvePaymentMethod(commandDTO),
                commandDTO.getCurrency(),
                commandDTO.getBillingCardHolderInfo() == null ? null : commandDTO.getBillingCardHolderInfo().getCountry());
        List<MerchantChannelMidBindingDO> bindings = midBindingMapper.selectList(Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED)
                .eq(MerchantChannelMidBindingDO::getBindingStatus, ENABLED)
                .eq(MerchantChannelMidBindingDO::getMerchantId, commandDTO.getMerchantId()));
        LocalDateTime now = LocalDateTime.now();
        List<RouteCandidate> candidates = bindings.stream()
                .filter(binding -> isActive(now, binding.getEffectiveTime(), binding.getExpireTime()))
                .map(binding -> toCandidate(binding, commandDTO, now))
                .filter(candidate -> candidate != null)
                .toList();
        log.info("event: PAYMENT_ROUTE_CANDIDATES stage=ROUTE merchantId: {} merchantOrderNo: {} transactionType: {} paymentMethod: {} currency: {} bindingCount: {} candidateCount: {} candidates: {}",
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getTransactionType(),
                resolvePaymentMethod(commandDTO),
                commandDTO.getCurrency(),
                bindings.size(),
                candidates.size(),
                candidateSummary(candidates));
        if (candidates.isEmpty()) {
            log.warn("event: PAYMENT_ROUTE_NO_CANDIDATE merchantId: {} merchantOrderNo: {} transactionType: {} paymentMethod: {} currency: {} bindingCount: {} candidateCount: {} durationMs: {}",
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    commandDTO.getTransactionType(),
                    resolvePaymentMethod(commandDTO),
                    commandDTO.getCurrency(),
                    bindings.size(),
                    candidates.size(),
                    elapsedMillis(startNanos));
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户未配置可用渠道MID");
        }
        if (candidates.size() > 1) {
            log.warn("event: PAYMENT_ROUTE_MULTI_CANDIDATE merchantId: {} merchantOrderNo: {} transactionType: {} paymentMethod: {} currency: {} candidateCount: {} candidates: {} durationMs: {}",
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    commandDTO.getTransactionType(),
                    resolvePaymentMethod(commandDTO),
                    commandDTO.getCurrency(),
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
        resultDTO.setMetadataValues(parseMetadata(candidate.midConfig().getMetadataValueJson()));
        resultDTO.setRequestedCurrency(normalize(commandDTO.getCurrency()));
        resultDTO.setRoutedCurrency(candidate.routedCurrency());
        resultDTO.setEdcRequired(candidate.edcRequired());
        resultDTO.setCapabilityId(candidate.capability().getId());
        resultDTO.setSupportedCurrencies(candidate.supportedCurrencies());
        resultDTO.setRouteReason("MERCHANT_MID_BINDING");
        log.info("event: PAYMENT_ROUTE_END merchantId: {} merchantOrderNo: {} transactionType: {} channelCode: {} channelId: {} midConfigId: {} midNo: {} capabilityId: {} supportedCurrencies: {} requestedCurrency: {} routedCurrency: {} edcRequired: {} endpointHost: {} connectTimeoutSeconds: {} readTimeoutSeconds: {} routeReason: {} durationMs: {}",
                commandDTO.getMerchantId(),
                commandDTO.getMerchantOrderNo(),
                commandDTO.getTransactionType(),
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
        log.info("event: PAYMENT_ROUTE_RESTORED channelCode: {} channelId: {} midConfigId: {} resolvedChannelCode: {} routeReason: {} durationMs: {}",
                channelCode,
                channelId,
                midConfigId,
                resultDTO.getChannelCode(),
                resultDTO.getRouteReason(),
                elapsedMillis(startNanos));
        return resultDTO;
    }

    private RouteCandidate toCandidate(MerchantChannelMidBindingDO binding,
                                       PaymentCreateCommandDTO commandDTO,
                                       LocalDateTime now) {
        ChannelMidConfigDO midConfig = midConfigMapper.selectOne(Wrappers.<ChannelMidConfigDO>lambdaQuery()
                .eq(ChannelMidConfigDO::getDeleted, NOT_DELETED)
                .eq(ChannelMidConfigDO::getId, binding.getMidConfigId())
                .eq(ChannelMidConfigDO::getMidStatus, ENABLED));
        if (midConfig == null || !isActive(now, midConfig.getEffectiveTime(), midConfig.getExpireTime())) {
            return null;
        }
        ChannelInfoDO channelInfo = channelInfoMapper.selectOne(Wrappers.<ChannelInfoDO>lambdaQuery()
                .eq(ChannelInfoDO::getDeleted, NOT_DELETED)
                .eq(ChannelInfoDO::getId, midConfig.getChannelId())
                .eq(ChannelInfoDO::getChannelStatus, ENABLED)
                .eq(ChannelInfoDO::getSupportAcquiring, ENABLED));
        if (channelInfo == null) {
            return null;
        }
        if (!BUSINESS_ACQUIRING.equals(normalize(midConfig.getBusinessType()))) {
            return null;
        }
        String paymentMethod = resolvePaymentMethod(commandDTO);
        if (!matchesScope(midConfig.getPaymentMethodScope(), paymentMethod)) {
            return null;
        }
        if (!matchesScope(midConfig.getTransactionTypeScope(), commandDTO.getTransactionType())) {
            return null;
        }
        String payerCountry = commandDTO.getBillingCardHolderInfo() == null ? null : commandDTO.getBillingCardHolderInfo().getCountry();
        if (!matchesScope(midConfig.getAllowedCountryScope(), payerCountry)) {
            return null;
        }
        List<ChannelPaymentCapabilityDO> capabilities = capabilityMapper.selectList(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, channelInfo.getId())
                .eq(ChannelPaymentCapabilityDO::getBusinessType, BUSINESS_ACQUIRING)
                .eq(ChannelPaymentCapabilityDO::getPaymentMethod, paymentMethod)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED)
                .orderByAsc(ChannelPaymentCapabilityDO::getSortOrder)
                .orderByAsc(ChannelPaymentCapabilityDO::getId));
        for (ChannelPaymentCapabilityDO capability : capabilities) {
            if (!matchesTransactionType(capability.getTransactionType(), commandDTO.getTransactionType())) {
                continue;
            }
            List<String> currencies = resolveSupportedCurrencies(capability, midConfig);
            if (currencies.isEmpty()) {
                continue;
            }
            String requestedCurrency = normalize(commandDTO.getCurrency());
            boolean directCurrencySupported = currencies.stream().anyMatch(item -> item.equals(requestedCurrency));
            String routedCurrency = directCurrencySupported ? requestedCurrency : currencies.get(0);
            return new RouteCandidate(channelInfo, midConfig, capability, currencies, routedCurrency, !directCurrencySupported);
        }
        return null;
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
     * 执行 matches Scope 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultPaymentChannelRouteService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param scope scope 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 matches Transaction Type 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultPaymentChannelRouteService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param capabilityTransactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
     * @param requestedTransactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private boolean matchesTransactionType(String capabilityTransactionType, String requestedTransactionType) {
        return matchesScope(capabilityTransactionType, requestedTransactionType);
    }

    /**
     * 执行 resolve Supported Currencies 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultPaymentChannelRouteService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param capability capability 输入值，含义由调用方法名称和所属业务对象限定
     * @param midConfig mid Config 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private List<String> resolveSupportedCurrencies(ChannelPaymentCapabilityDO capability, ChannelMidConfigDO midConfig) {
        List<String> capabilityCurrencies = capabilityCurrencyMapper.selectList(Wrappers.<ChannelCapabilityCurrencyDO>lambdaQuery()
                        .eq(ChannelCapabilityCurrencyDO::getDeleted, NOT_DELETED)
                        .eq(ChannelCapabilityCurrencyDO::getCapabilityId, capability.getId())
                        .eq(ChannelCapabilityCurrencyDO::getCurrencyStatus, ENABLED)
                        .orderByAsc(ChannelCapabilityCurrencyDO::getCurrencyCode))
                .stream()
                .map(ChannelCapabilityCurrencyDO::getCurrencyCode)
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .distinct()
                .toList();
        Set<String> midCurrencies = parseScopeValues(midConfig.getCurrencyScope());
        if (midCurrencies.isEmpty()) {
            return capabilityCurrencies;
        }
        List<String> result = new ArrayList<>();
        for (String currency : capabilityCurrencies) {
            if (midCurrencies.contains(currency)) {
                result.add(currency);
            }
        }
        return result;
    }

    /**
     * 执行 parse Scope Values 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultPaymentChannelRouteService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param scope scope 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析后的内部数据结构或业务值
     */
    private Set<String> parseScopeValues(String scope) {
        if (!StringUtils.hasText(scope) || ALL.equalsIgnoreCase(scope.trim())) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : scope.split(SCOPE_SEPARATOR)) {
            String normalized = normalize(item);
            if (StringUtils.hasText(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    /**
     * 执行 normalize 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultPaymentChannelRouteService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 标准化后的业务字段值
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
     * 执行 resolve Payment Method 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultPaymentChannelRouteService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param commandDTO command DTO 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolvePaymentMethod(PaymentCreateCommandDTO commandDTO) {
        return StringUtils.hasText(commandDTO.getPaymentMethod()) ? normalize(commandDTO.getPaymentMethod()) : DEFAULT_PAYMENT_METHOD;
    }

    /**
     * 执行 elapsed Millis 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultPaymentChannelRouteService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param startNanos start Nanos 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 执行 parse Metadata 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultPaymentChannelRouteService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param metadataValueJson metadata Value Json 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析后的内部数据结构或业务值
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
