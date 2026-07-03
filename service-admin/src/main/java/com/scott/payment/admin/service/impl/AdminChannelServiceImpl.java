package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.channel.ChannelDTOs.AccessQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.AccessResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.AccessSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilitySaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelOption;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitSaveRequest;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelAccessConfigDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCardBrandDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCurrencyDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelLimitRuleDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelPaymentCapabilityDO;
import com.scott.payment.admin.mapper.ChannelAccessConfigMapper;
import com.scott.payment.admin.mapper.ChannelCapabilityCardBrandMapper;
import com.scott.payment.admin.mapper.ChannelCapabilityCurrencyMapper;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.admin.mapper.ChannelLimitRuleMapper;
import com.scott.payment.admin.mapper.ChannelPaymentCapabilityMapper;
import com.scott.payment.admin.mapper.SysDictDataMapper;
import com.scott.payment.admin.service.AdminChannelService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 管理后台渠道管理服务实现。
 *
 * <p>该服务只维护渠道基础数据、能力、限额和接入配置，不执行真实渠道调用，也不承载支付交易状态机。</p>
 */
@Service
public class AdminChannelServiceImpl implements AdminChannelService {

    private static final long NOT_DELETED = 0L;
    private static final int ENABLED = 1;
    private static final int DISABLED = 0;
    private static final String BUSINESS_ACQUIRING = "ACQUIRING";
    private static final String BUSINESS_PAYOUT = "PAYOUT";
    private static final String PAYMENT_BANK_CARD = "BANK_CARD";
    private static final String NONE = "NONE";
    private static final String ALL = "ALL";
    private static final String USD = "USD";
    private static final String TRANSACTION_TYPE_SEPARATOR = ",";
    private static final Pattern CHANNEL_CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,64}$");
    private static final Set<String> INCREMENTAL_TRANSACTION_TYPES = Set.of("AUTHORIZATION", "PRE_AUTHORIZATION");

    private final ChannelInfoMapper channelInfoMapper;
    private final ChannelPaymentCapabilityMapper capabilityMapper;
    private final ChannelCapabilityCurrencyMapper capabilityCurrencyMapper;
    private final ChannelCapabilityCardBrandMapper capabilityCardBrandMapper;
    private final ChannelLimitRuleMapper limitRuleMapper;
    private final ChannelAccessConfigMapper accessConfigMapper;
    private final SysDictDataMapper dictDataMapper;

    public AdminChannelServiceImpl(ChannelInfoMapper channelInfoMapper,
                                   ChannelPaymentCapabilityMapper capabilityMapper,
                                   ChannelCapabilityCurrencyMapper capabilityCurrencyMapper,
                                   ChannelCapabilityCardBrandMapper capabilityCardBrandMapper,
                                   ChannelLimitRuleMapper limitRuleMapper,
                                   ChannelAccessConfigMapper accessConfigMapper,
                                   SysDictDataMapper dictDataMapper) {
        this.channelInfoMapper = channelInfoMapper;
        this.capabilityMapper = capabilityMapper;
        this.capabilityCurrencyMapper = capabilityCurrencyMapper;
        this.capabilityCardBrandMapper = capabilityCardBrandMapper;
        this.limitRuleMapper = limitRuleMapper;
        this.accessConfigMapper = accessConfigMapper;
        this.dictDataMapper = dictDataMapper;
    }

    @Override
    public PageResult<ChannelInfoResponse> pageChannels(ChannelInfoQuery request) {
        ChannelInfoQuery query = request == null ? new ChannelInfoQuery() : request;
        Page<ChannelInfoDO> page = channelInfoMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<ChannelInfoDO>lambdaQuery()
                        .eq(ChannelInfoDO::getDeleted, NOT_DELETED)
                        .eq(query.getChannelStatus() != null, ChannelInfoDO::getChannelStatus, query.getChannelStatus())
                        .eq(query.getSupportAcquiring() != null, ChannelInfoDO::getSupportAcquiring, query.getSupportAcquiring())
                        .eq(query.getSupportPayout() != null, ChannelInfoDO::getSupportPayout, query.getSupportPayout())
                        .eq(query.getSupport3ds() != null, ChannelInfoDO::getSupport3ds, query.getSupport3ds())
                        .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                                .like(ChannelInfoDO::getChannelCode, trim(query.getKeyword()))
                                .or().like(ChannelInfoDO::getChannelCnName, trim(query.getKeyword()))
                                .or().like(ChannelInfoDO::getChannelEnName, trim(query.getKeyword())))
                        .orderByAsc(ChannelInfoDO::getSortOrder)
                        .orderByDesc(ChannelInfoDO::getUpdateTime)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toChannelResponse).toList());
    }

    @Override
    public List<ChannelOption> listChannelOptions() {
        return channelInfoMapper.selectList(Wrappers.<ChannelInfoDO>lambdaQuery()
                        .eq(ChannelInfoDO::getDeleted, NOT_DELETED)
                        .orderByAsc(ChannelInfoDO::getSortOrder)
                        .orderByAsc(ChannelInfoDO::getChannelCode))
                .stream()
                .map(channel -> {
                    ChannelOption option = new ChannelOption();
                    option.setId(channel.getId());
                    option.setChannelCode(channel.getChannelCode());
                    option.setChannelName(channelName(channel));
                    option.setChannelStatus(channel.getChannelStatus());
                    option.setSupportAcquiring(channel.getSupportAcquiring());
                    option.setSupportPayout(channel.getSupportPayout());
                    return option;
                })
                .toList();
    }

    @Override
    public ChannelInfoResponse getChannel(Long id) {
        return toChannelResponse(findChannel(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelInfoResponse createChannel(ChannelInfoSaveRequest request) {
        validateChannelRequest(request, null);
        ChannelInfoDO entity = new ChannelInfoDO();
        fillChannel(entity, request, LocalDateTime.now());
        entity.setCreateTime(entity.getUpdateTime());
        entity.setDeleted(NOT_DELETED);
        channelInfoMapper.insert(entity);
        return toChannelResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelInfoResponse updateChannel(Long id, ChannelInfoSaveRequest request) {
        ChannelInfoDO entity = findChannel(id);
        validateChannelRequest(request, id);
        fillChannel(entity, request, LocalDateTime.now());
        channelInfoMapper.updateById(entity);
        return toChannelResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelInfoResponse updateChannelStatus(Long id, Integer status) {
        ChannelInfoDO entity = findChannel(id);
        validateStatus(status);
        entity.setChannelStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        channelInfoMapper.updateById(entity);
        return toChannelResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChannel(Long id) {
        ChannelInfoDO entity = findChannel(id);
        if (hasActiveCapability(id) || hasActiveLimit(id) || hasActiveAccessConfig(id)) {
            throw badRequest("渠道存在启用中的能力、限额或接入配置，不能删除");
        }
        entity.setDeleted(entity.getId());
        entity.setUpdateTime(LocalDateTime.now());
        channelInfoMapper.updateById(entity);
    }

    @Override
    public PageResult<CapabilityResponse> pageCapabilities(CapabilityQuery request) {
        CapabilityQuery query = request == null ? new CapabilityQuery() : request;
        Page<ChannelPaymentCapabilityDO> page = capabilityMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildCapabilityQuery(query)
        );
        Map<Long, ChannelInfoDO> channelMap = channelMap(page.getRecords().stream().map(ChannelPaymentCapabilityDO::getChannelId).toList());
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(row -> toCapabilityResponse(row, channelMap.get(row.getChannelId()))).toList());
    }

    @Override
    public CapabilityResponse getCapability(Long id) {
        ChannelPaymentCapabilityDO entity = findCapability(id);
        return toCapabilityResponse(entity, findChannel(entity.getChannelId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapabilityResponse createCapability(CapabilitySaveRequest request) {
        ChannelInfoDO channel = validateCapabilityRequest(request, null);
        ChannelPaymentCapabilityDO entity = new ChannelPaymentCapabilityDO();
        fillCapability(entity, request, channel, LocalDateTime.now());
        entity.setCreateTime(entity.getUpdateTime());
        entity.setDeleted(NOT_DELETED);
        capabilityMapper.insert(entity);
        replaceCapabilityCurrencies(entity, request.getCurrencyCodes());
        replaceCapabilityCardBrands(entity, request.getCardBrands());
        return toCapabilityResponse(entity, channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapabilityResponse updateCapability(Long id, CapabilitySaveRequest request) {
        ChannelPaymentCapabilityDO entity = findCapability(id);
        ChannelInfoDO channel = validateCapabilityRequest(request, id);
        fillCapability(entity, request, channel, LocalDateTime.now());
        capabilityMapper.updateById(entity);
        replaceCapabilityCurrencies(entity, request.getCurrencyCodes());
        replaceCapabilityCardBrands(entity, request.getCardBrands());
        return toCapabilityResponse(entity, channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapabilityResponse updateCapabilityStatus(Long id, Integer status) {
        ChannelPaymentCapabilityDO entity = findCapability(id);
        validateStatus(status);
        if (status == ENABLED) {
            ChannelInfoDO channel = findChannel(entity.getChannelId());
            validateChannelSupportsBusiness(channel, entity.getBusinessType());
        }
        entity.setCapabilityStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        capabilityMapper.updateById(entity);
        return toCapabilityResponse(entity, findChannel(entity.getChannelId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapabilityResponse updateCapabilitySupport(Long id, Integer support3ds, Integer supportIncrementalAuthorization) {
        ChannelPaymentCapabilityDO entity = findCapability(id);
        if (support3ds != null) {
            validateStatus(support3ds);
            entity.setSupport3ds(support3ds);
        }
        if (supportIncrementalAuthorization != null) {
            validateStatus(supportIncrementalAuthorization);
            if (supportIncrementalAuthorization == ENABLED
                    && splitTransactionTypes(entity.getBusinessType(), entity.getTransactionType()).stream().noneMatch(INCREMENTAL_TRANSACTION_TYPES::contains)) {
                throw badRequest("增量授权只允许配置在授权或预授权交易类型下");
            }
            entity.setSupportIncrementalAuthorization(supportIncrementalAuthorization);
        }
        entity.setUpdateTime(LocalDateTime.now());
        capabilityMapper.updateById(entity);
        return toCapabilityResponse(entity, findChannel(entity.getChannelId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCapability(Long id) {
        ChannelPaymentCapabilityDO entity = findCapability(id);
        entity.setDeleted(entity.getId());
        entity.setUpdateTime(LocalDateTime.now());
        capabilityMapper.updateById(entity);
        softDeleteCapabilityChildren(id);
    }

    @Override
    public PageResult<LimitResponse> pageLimits(LimitQuery request) {
        LimitQuery query = request == null ? new LimitQuery() : request;
        Page<ChannelLimitRuleDO> page = limitRuleMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildLimitQuery(query)
        );
        Map<Long, ChannelInfoDO> channelMap = channelMap(page.getRecords().stream().map(ChannelLimitRuleDO::getChannelId).toList());
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(row -> toLimitResponse(row, channelMap.get(row.getChannelId()))).toList());
    }

    @Override
    public LimitResponse getLimit(Long id) {
        ChannelLimitRuleDO entity = findLimit(id);
        return toLimitResponse(entity, findChannel(entity.getChannelId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LimitResponse createLimit(LimitSaveRequest request) {
        ChannelInfoDO channel = validateLimitRequest(request, null);
        ChannelLimitRuleDO entity = new ChannelLimitRuleDO();
        fillLimit(entity, request, channel, LocalDateTime.now());
        entity.setCreateTime(entity.getUpdateTime());
        entity.setDeleted(NOT_DELETED);
        limitRuleMapper.insert(entity);
        return toLimitResponse(entity, channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LimitResponse updateLimit(Long id, LimitSaveRequest request) {
        ChannelLimitRuleDO entity = findLimit(id);
        ChannelInfoDO channel = validateLimitRequest(request, id);
        fillLimit(entity, request, channel, LocalDateTime.now());
        limitRuleMapper.updateById(entity);
        return toLimitResponse(entity, channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LimitResponse updateLimitStatus(Long id, Integer status) {
        ChannelLimitRuleDO entity = findLimit(id);
        validateStatus(status);
        entity.setRuleStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        limitRuleMapper.updateById(entity);
        return toLimitResponse(entity, findChannel(entity.getChannelId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLimit(Long id) {
        ChannelLimitRuleDO entity = findLimit(id);
        entity.setDeleted(entity.getId());
        entity.setUpdateTime(LocalDateTime.now());
        limitRuleMapper.updateById(entity);
    }

    @Override
    public PageResult<AccessResponse> pageAccessConfigs(AccessQuery request) {
        AccessQuery query = request == null ? new AccessQuery() : request;
        Page<ChannelAccessConfigDO> page = accessConfigMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildAccessQuery(query)
        );
        Map<Long, ChannelInfoDO> channelMap = channelMap(page.getRecords().stream().map(ChannelAccessConfigDO::getChannelId).toList());
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(row -> toAccessResponse(row, channelMap.get(row.getChannelId()))).toList());
    }

    @Override
    public AccessResponse getAccessConfig(Long id) {
        ChannelAccessConfigDO entity = findAccess(id);
        return toAccessResponse(entity, findChannel(entity.getChannelId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccessResponse createAccessConfig(AccessSaveRequest request) {
        ChannelInfoDO channel = validateAccessRequest(request, null);
        ChannelAccessConfigDO entity = new ChannelAccessConfigDO();
        fillAccess(entity, request, channel, LocalDateTime.now(), true);
        entity.setCreateTime(entity.getUpdateTime());
        entity.setDeleted(NOT_DELETED);
        accessConfigMapper.insert(entity);
        return toAccessResponse(entity, channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccessResponse updateAccessConfig(Long id, AccessSaveRequest request) {
        ChannelAccessConfigDO entity = findAccess(id);
        ChannelInfoDO channel = validateAccessRequest(request, id);
        fillAccess(entity, request, channel, LocalDateTime.now(), false);
        accessConfigMapper.updateById(entity);
        return toAccessResponse(entity, channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccessResponse updateAccessConfigStatus(Long id, Integer status) {
        ChannelAccessConfigDO entity = findAccess(id);
        validateStatus(status);
        entity.setConfigStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        accessConfigMapper.updateById(entity);
        return toAccessResponse(entity, findChannel(entity.getChannelId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccessConfig(Long id) {
        ChannelAccessConfigDO entity = findAccess(id);
        entity.setDeleted(entity.getId());
        entity.setUpdateTime(LocalDateTime.now());
        accessConfigMapper.updateById(entity);
    }

    private void validateChannelRequest(ChannelInfoSaveRequest request, Long id) {
        String code = normalizeCode(request.getChannelCode());
        if (!CHANNEL_CODE_PATTERN.matcher(code).matches()) {
            throw badRequest("渠道编码只能包含大写字母、数字、下划线，长度2-64");
        }
        validateStatus(request.getChannelStatus());
        validateStatus(request.getSupportAcquiring());
        validateStatus(request.getSupportPayout());
        validateStatus(request.getSupport3ds());
        Long count = channelInfoMapper.selectCount(Wrappers.<ChannelInfoDO>lambdaQuery()
                .eq(ChannelInfoDO::getDeleted, NOT_DELETED)
                .eq(ChannelInfoDO::getChannelCode, code)
                .ne(id != null, ChannelInfoDO::getId, id));
        if (count > 0) {
            throw badRequest("渠道编码已存在");
        }
    }

    private ChannelInfoDO validateCapabilityRequest(CapabilitySaveRequest request, Long id) {
        ChannelInfoDO channel = findChannel(request.getChannelId());
        String businessType = normalizeCode(request.getBusinessType());
        String paymentMethod = normalizeCode(request.getPaymentMethod());
        List<String> transactionTypes = normalizeTransactionTypes(businessType, request.getTransactionTypes(), request.getTransactionType());
        String transactionType = joinTransactionTypes(transactionTypes);
        validateBusinessType(businessType);
        validateChannelSupportsBusiness(channel, businessType);
        validatePaymentMethod(businessType, paymentMethod);
        if (BUSINESS_ACQUIRING.equals(businessType) && transactionTypes.isEmpty()) {
            throw badRequest("收单能力必须配置交易类型");
        }
        if (request.getCapabilityStatus() != null) {
            validateStatus(request.getCapabilityStatus());
        }
        validateStatus(defaultZero(request.getSupport3ds()));
        validateStatus(defaultZero(request.getSupportIncrementalAuthorization()));
        if (defaultZero(request.getSupportIncrementalAuthorization()) == ENABLED
                && transactionTypes.stream().noneMatch(INCREMENTAL_TRANSACTION_TYPES::contains)) {
            throw badRequest("增量授权只允许配置在授权或预授权交易类型下");
        }
        List<String> currencies = normalizeCodes(request.getCurrencyCodes());
        if (currencies.isEmpty()) {
            throw badRequest("允许交易币种不能为空");
        }
        currencies.forEach(currency -> assertDictValue("iso_currency", currency, false));
        List<String> cardBrands = normalizeCodes(request.getCardBrands());
        if (PAYMENT_BANK_CARD.equals(paymentMethod)) {
            if (cardBrands.isEmpty()) {
                throw badRequest("银行卡支付能力必须绑定卡品牌");
            }
            cardBrands.forEach(brand -> assertDictValue("card_brand", brand, true));
        } else if (!cardBrands.isEmpty()) {
            throw badRequest("非银行卡支付方式不能绑定卡品牌");
        }
        Long count = capabilityMapper.selectCount(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, request.getChannelId())
                .eq(ChannelPaymentCapabilityDO::getBusinessType, businessType)
                .eq(ChannelPaymentCapabilityDO::getPaymentMethod, paymentMethod)
                .ne(id != null, ChannelPaymentCapabilityDO::getId, id));
        if (count > 0) {
            throw badRequest("同一渠道、业务类型和支付方式不能重复，请在同一条能力中维护交易类型");
        }
        request.setBusinessType(businessType);
        request.setPaymentMethod(paymentMethod);
        request.setTransactionType(transactionType);
        request.setTransactionTypes(transactionTypes);
        request.setCurrencyCodes(currencies);
        request.setCardBrands(cardBrands);
        return channel;
    }

    private ChannelInfoDO validateLimitRequest(LimitSaveRequest request, Long id) {
        ChannelInfoDO channel = findChannel(request.getChannelId());
        String businessType = normalizeCode(request.getBusinessType());
        validateBusinessType(businessType);
        String paymentMethod = defaultScope(request.getPaymentMethod());
        String transactionType = ALL;
        String cardBrand = defaultScope(request.getCardBrand());
        String limitType = normalizeCode(request.getLimitType());
        if (request.getLimitAmount() == null || request.getLimitAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw badRequest("限额金额必须大于等于0");
        }
        if (request.getEffectiveStartTime() != null && request.getEffectiveEndTime() != null
                && request.getEffectiveEndTime().isBefore(request.getEffectiveStartTime())) {
            throw badRequest("生效结束时间不能早于生效开始时间");
        }
        validateStatus(request.getRuleStatus());
        Long count = limitRuleMapper.selectCount(Wrappers.<ChannelLimitRuleDO>lambdaQuery()
                .eq(ChannelLimitRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelLimitRuleDO::getRuleStatus, ENABLED)
                .eq(ChannelLimitRuleDO::getChannelId, request.getChannelId())
                .eq(ChannelLimitRuleDO::getBusinessType, businessType)
                .eq(ChannelLimitRuleDO::getPaymentMethod, paymentMethod)
                .eq(ChannelLimitRuleDO::getCardBrand, cardBrand)
                .eq(ChannelLimitRuleDO::getLimitType, limitType)
                .ne(id != null, ChannelLimitRuleDO::getId, id));
        if (request.getRuleStatus() == ENABLED && count > 0) {
            throw badRequest("同一限额作用范围下已存在启用规则");
        }
        request.setBusinessType(businessType);
        request.setPaymentMethod(paymentMethod);
        request.setTransactionType(transactionType);
        request.setCardBrand(cardBrand);
        request.setLimitType(limitType);
        return channel;
    }

    private ChannelInfoDO validateAccessRequest(AccessSaveRequest request, Long id) {
        ChannelInfoDO channel = findChannel(request.getChannelId());
        String envMode = normalizeCode(request.getEnvMode());
        String interactionMode = normalizeCode(request.getInteractionMode());
        if (!Set.of("TEST", "PROD").contains(envMode)) {
            throw badRequest("环境必须为 TEST 或 PROD");
        }
        validateStatus(request.getConfigStatus());
        if (interactionMode.contains("API") && id == null
                && (!StringUtils.hasText(request.getApiKey()) || !StringUtils.hasText(request.getApiSecret()))) {
            throw badRequest("API 类交互方式必须填写 API Key 和 API Secret");
        }
        if ((interactionMode.contains("CERT") || interactionMode.contains("TLS"))
                && !StringUtils.hasText(request.getClientCertPath()) && id == null) {
            throw badRequest("证书类交互方式必须填写客户端证书路径");
        }
        Long count = accessConfigMapper.selectCount(Wrappers.<ChannelAccessConfigDO>lambdaQuery()
                .eq(ChannelAccessConfigDO::getDeleted, NOT_DELETED)
                .eq(ChannelAccessConfigDO::getChannelId, request.getChannelId())
                .eq(ChannelAccessConfigDO::getEnvMode, envMode)
                .ne(id != null, ChannelAccessConfigDO::getId, id));
        if (count > 0) {
            throw badRequest("同一渠道同一环境只能配置一条接入配置");
        }
        request.setEnvMode(envMode);
        request.setInteractionMode(interactionMode);
        return channel;
    }

    private void fillChannel(ChannelInfoDO entity, ChannelInfoSaveRequest request, LocalDateTime now) {
        entity.setChannelCode(normalizeCode(request.getChannelCode()));
        entity.setChannelCnName(trim(request.getChannelCnName()));
        entity.setChannelEnName(trim(request.getChannelEnName()));
        entity.setChannelStatus(request.getChannelStatus());
        entity.setSupportAcquiring(request.getSupportAcquiring());
        entity.setSupportPayout(request.getSupportPayout());
        entity.setSupport3ds(request.getSupport3ds());
        entity.setDefaultRequestUrl(trimToNull(request.getDefaultRequestUrl()));
        entity.setDefaultInteractionMode(trimToNull(request.getDefaultInteractionMode()));
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setUpdateTime(now);
    }

    private void fillCapability(ChannelPaymentCapabilityDO entity, CapabilitySaveRequest request,
                                ChannelInfoDO channel, LocalDateTime now) {
        entity.setChannelId(channel.getId());
        entity.setChannelCode(channel.getChannelCode());
        entity.setBusinessType(request.getBusinessType());
        entity.setPaymentMethod(request.getPaymentMethod());
        entity.setTransactionType(request.getTransactionType());
        entity.setSupport3ds(defaultZero(request.getSupport3ds()));
        entity.setSupportIncrementalAuthorization(defaultZero(request.getSupportIncrementalAuthorization()));
        entity.setCapabilityStatus(request.getCapabilityStatus());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setUpdateTime(now);
    }

    private void fillLimit(ChannelLimitRuleDO entity, LimitSaveRequest request, ChannelInfoDO channel, LocalDateTime now) {
        entity.setChannelId(channel.getId());
        entity.setChannelCode(channel.getChannelCode());
        entity.setBusinessType(request.getBusinessType());
        entity.setPaymentMethod(request.getPaymentMethod());
        entity.setTransactionType(request.getTransactionType());
        entity.setCardBrand(request.getCardBrand());
        entity.setLimitType(request.getLimitType());
        entity.setLimitCurrency(USD);
        entity.setLimitAmount(request.getLimitAmount());
        entity.setEffectiveStartTime(request.getEffectiveStartTime());
        entity.setEffectiveEndTime(request.getEffectiveEndTime());
        entity.setRuleStatus(request.getRuleStatus());
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setUpdateTime(now);
    }

    private void fillAccess(ChannelAccessConfigDO entity, AccessSaveRequest request, ChannelInfoDO channel,
                            LocalDateTime now, boolean create) {
        entity.setChannelId(channel.getId());
        entity.setChannelCode(channel.getChannelCode());
        entity.setEnvMode(request.getEnvMode());
        entity.setBaseUrl(trim(request.getBaseUrl()));
        entity.setCallbackUrl(trimToNull(request.getCallbackUrl()));
        entity.setInteractionMode(request.getInteractionMode());
        entity.setChannelMerchantNo(trimToNull(request.getChannelMerchantNo()));
        if (StringUtils.hasText(request.getApiKey())) {
            entity.setApiKeyCipher(cipher(request.getApiKey()));
        } else if (create) {
            entity.setApiKeyCipher(null);
        }
        if (StringUtils.hasText(request.getApiSecret())) {
            entity.setApiSecretCipher(cipher(request.getApiSecret()));
        } else if (create) {
            entity.setApiSecretCipher(null);
        }
        entity.setClientCertPath(trimToNull(request.getClientCertPath()));
        if (StringUtils.hasText(request.getClientCertPassword())) {
            entity.setClientCertPasswordCipher(cipher(request.getClientCertPassword()));
        } else if (create) {
            entity.setClientCertPasswordCipher(null);
        }
        entity.setServerCertPath(trimToNull(request.getServerCertPath()));
        entity.setExtraConfigJson(trimToNull(request.getExtraConfigJson()));
        entity.setConfigStatus(request.getConfigStatus());
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setUpdateTime(now);
    }

    private ChannelInfoResponse toChannelResponse(ChannelInfoDO entity) {
        ChannelInfoResponse response = new ChannelInfoResponse();
        response.setId(entity.getId());
        response.setChannelCode(entity.getChannelCode());
        response.setChannelCnName(entity.getChannelCnName());
        response.setChannelEnName(entity.getChannelEnName());
        response.setChannelStatus(entity.getChannelStatus());
        response.setSupportAcquiring(entity.getSupportAcquiring());
        response.setSupportPayout(entity.getSupportPayout());
        response.setSupport3ds(entity.getSupport3ds());
        response.setDefaultRequestUrl(entity.getDefaultRequestUrl());
        response.setDefaultInteractionMode(entity.getDefaultInteractionMode());
        response.setSortOrder(entity.getSortOrder());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        Map<String, List<String>> methods = capabilityMethods(entity.getId());
        response.setAcquiringPaymentMethods(methods.getOrDefault(BUSINESS_ACQUIRING, List.of()));
        response.setPayoutPaymentMethods(methods.getOrDefault(BUSINESS_PAYOUT, List.of()));
        return response;
    }

    private CapabilityResponse toCapabilityResponse(ChannelPaymentCapabilityDO entity, ChannelInfoDO channel) {
        CapabilityResponse response = new CapabilityResponse();
        response.setId(entity.getId());
        response.setChannelId(entity.getChannelId());
        response.setChannelCode(entity.getChannelCode());
        response.setChannelName(channelName(channel));
        response.setBusinessType(entity.getBusinessType());
        response.setPaymentMethod(entity.getPaymentMethod());
        response.setTransactionType(entity.getTransactionType());
        response.setTransactionTypes(splitTransactionTypes(entity.getBusinessType(), entity.getTransactionType()));
        response.setCurrencyCodes(listCapabilityCurrencies(entity.getId()));
        response.setCardBrands(listCapabilityCardBrands(entity.getId()));
        response.setSupport3ds(entity.getSupport3ds());
        response.setSupportIncrementalAuthorization(entity.getSupportIncrementalAuthorization());
        response.setCapabilityStatus(entity.getCapabilityStatus());
        response.setSortOrder(entity.getSortOrder());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private LimitResponse toLimitResponse(ChannelLimitRuleDO entity, ChannelInfoDO channel) {
        LimitResponse response = new LimitResponse();
        response.setId(entity.getId());
        response.setChannelId(entity.getChannelId());
        response.setChannelCode(entity.getChannelCode());
        response.setChannelName(channelName(channel));
        response.setBusinessType(entity.getBusinessType());
        response.setPaymentMethod(entity.getPaymentMethod());
        response.setTransactionType(entity.getTransactionType());
        response.setCardBrand(entity.getCardBrand());
        response.setLimitType(entity.getLimitType());
        response.setLimitCurrency(entity.getLimitCurrency());
        response.setLimitAmount(entity.getLimitAmount());
        response.setEffectiveStartTime(entity.getEffectiveStartTime());
        response.setEffectiveEndTime(entity.getEffectiveEndTime());
        response.setRuleStatus(entity.getRuleStatus());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private AccessResponse toAccessResponse(ChannelAccessConfigDO entity, ChannelInfoDO channel) {
        AccessResponse response = new AccessResponse();
        response.setId(entity.getId());
        response.setChannelId(entity.getChannelId());
        response.setChannelCode(entity.getChannelCode());
        response.setChannelName(channelName(channel));
        response.setEnvMode(entity.getEnvMode());
        response.setBaseUrl(entity.getBaseUrl());
        response.setCallbackUrl(entity.getCallbackUrl());
        response.setInteractionMode(entity.getInteractionMode());
        response.setChannelMerchantNo(entity.getChannelMerchantNo());
        response.setApiKeyMasked(maskCipher(entity.getApiKeyCipher()));
        response.setApiSecretMasked(maskCipher(entity.getApiSecretCipher()));
        response.setClientCertPath(entity.getClientCertPath());
        response.setClientCertPasswordMasked(maskCipher(entity.getClientCertPasswordCipher()));
        response.setServerCertPath(entity.getServerCertPath());
        response.setExtraConfigJson(entity.getExtraConfigJson());
        response.setConfigStatus(entity.getConfigStatus());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private LambdaQueryWrapper<ChannelPaymentCapabilityDO> buildCapabilityQuery(CapabilityQuery query) {
        LambdaQueryWrapper<ChannelPaymentCapabilityDO> wrapper = Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(query.getChannelId() != null, ChannelPaymentCapabilityDO::getChannelId, query.getChannelId())
                .eq(StringUtils.hasText(query.getBusinessType()), ChannelPaymentCapabilityDO::getBusinessType, normalizeCode(query.getBusinessType()))
                .eq(StringUtils.hasText(query.getPaymentMethod()), ChannelPaymentCapabilityDO::getPaymentMethod, normalizeCode(query.getPaymentMethod()))
                .eq(query.getCapabilityStatus() != null, ChannelPaymentCapabilityDO::getCapabilityStatus, query.getCapabilityStatus())
                .orderByAsc(ChannelPaymentCapabilityDO::getSortOrder)
                .orderByDesc(ChannelPaymentCapabilityDO::getUpdateTime);
        if (StringUtils.hasText(query.getTransactionType())) {
            String transactionType = normalizeCode(query.getTransactionType());
            wrapper.and(condition -> condition
                    .eq(ChannelPaymentCapabilityDO::getTransactionType, transactionType)
                    .or().likeRight(ChannelPaymentCapabilityDO::getTransactionType, transactionType + TRANSACTION_TYPE_SEPARATOR)
                    .or().like(ChannelPaymentCapabilityDO::getTransactionType, TRANSACTION_TYPE_SEPARATOR + transactionType + TRANSACTION_TYPE_SEPARATOR)
                    .or().likeLeft(ChannelPaymentCapabilityDO::getTransactionType, TRANSACTION_TYPE_SEPARATOR + transactionType));
        }
        List<Long> capabilityIds = capabilityIdsByCurrencyOrBrand(query);
        if (capabilityIds != null) {
            wrapper.in(capabilityIds.isEmpty(), ChannelPaymentCapabilityDO::getId, -1L)
                    .in(!capabilityIds.isEmpty(), ChannelPaymentCapabilityDO::getId, capabilityIds);
        }
        return wrapper;
    }

    private LambdaQueryWrapper<ChannelLimitRuleDO> buildLimitQuery(LimitQuery query) {
        return Wrappers.<ChannelLimitRuleDO>lambdaQuery()
                .eq(ChannelLimitRuleDO::getDeleted, NOT_DELETED)
                .eq(query.getChannelId() != null, ChannelLimitRuleDO::getChannelId, query.getChannelId())
                .eq(StringUtils.hasText(query.getBusinessType()), ChannelLimitRuleDO::getBusinessType, normalizeCode(query.getBusinessType()))
                .eq(StringUtils.hasText(query.getPaymentMethod()), ChannelLimitRuleDO::getPaymentMethod, normalizeCode(query.getPaymentMethod()))
                .eq(StringUtils.hasText(query.getCardBrand()), ChannelLimitRuleDO::getCardBrand, normalizeCode(query.getCardBrand()))
                .eq(StringUtils.hasText(query.getLimitType()), ChannelLimitRuleDO::getLimitType, normalizeCode(query.getLimitType()))
                .eq(query.getRuleStatus() != null, ChannelLimitRuleDO::getRuleStatus, query.getRuleStatus())
                .orderByDesc(ChannelLimitRuleDO::getUpdateTime);
    }

    private LambdaQueryWrapper<ChannelAccessConfigDO> buildAccessQuery(AccessQuery query) {
        return Wrappers.<ChannelAccessConfigDO>lambdaQuery()
                .eq(ChannelAccessConfigDO::getDeleted, NOT_DELETED)
                .eq(query.getChannelId() != null, ChannelAccessConfigDO::getChannelId, query.getChannelId())
                .eq(StringUtils.hasText(query.getEnvMode()), ChannelAccessConfigDO::getEnvMode, normalizeCode(query.getEnvMode()))
                .eq(StringUtils.hasText(query.getInteractionMode()), ChannelAccessConfigDO::getInteractionMode, normalizeCode(query.getInteractionMode()))
                .eq(query.getConfigStatus() != null, ChannelAccessConfigDO::getConfigStatus, query.getConfigStatus())
                .orderByDesc(ChannelAccessConfigDO::getUpdateTime);
    }

    private List<Long> capabilityIdsByCurrencyOrBrand(CapabilityQuery query) {
        boolean filterCurrency = StringUtils.hasText(query.getCurrencyCode());
        boolean filterBrand = StringUtils.hasText(query.getCardBrand());
        if (!filterCurrency && !filterBrand) {
            return null;
        }
        Set<Long> ids = null;
        if (filterCurrency) {
            ids = capabilityCurrencyMapper.selectList(Wrappers.<ChannelCapabilityCurrencyDO>lambdaQuery()
                            .eq(ChannelCapabilityCurrencyDO::getDeleted, NOT_DELETED)
                            .eq(ChannelCapabilityCurrencyDO::getCurrencyCode, normalizeCode(query.getCurrencyCode())))
                    .stream().map(ChannelCapabilityCurrencyDO::getCapabilityId).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (filterBrand) {
            Set<Long> brandIds = capabilityCardBrandMapper.selectList(Wrappers.<ChannelCapabilityCardBrandDO>lambdaQuery()
                            .eq(ChannelCapabilityCardBrandDO::getDeleted, NOT_DELETED)
                            .eq(ChannelCapabilityCardBrandDO::getCardBrand, normalizeCode(query.getCardBrand())))
                    .stream().map(ChannelCapabilityCardBrandDO::getCapabilityId).collect(Collectors.toCollection(LinkedHashSet::new));
            if (ids == null) {
                ids = brandIds;
            } else {
                ids.retainAll(brandIds);
            }
        }
        return new ArrayList<>(ids == null ? Set.of() : ids);
    }

    private void replaceCapabilityCurrencies(ChannelPaymentCapabilityDO capability, List<String> currencies) {
        capabilityCurrencyMapper.selectList(Wrappers.<ChannelCapabilityCurrencyDO>lambdaQuery()
                        .eq(ChannelCapabilityCurrencyDO::getDeleted, NOT_DELETED)
                        .eq(ChannelCapabilityCurrencyDO::getCapabilityId, capability.getId()))
                .forEach(row -> {
                    row.setDeleted(row.getId());
                    row.setUpdateTime(LocalDateTime.now());
                    capabilityCurrencyMapper.updateById(row);
                });
        for (String currency : normalizeCodes(currencies)) {
            ChannelCapabilityCurrencyDO row = new ChannelCapabilityCurrencyDO();
            row.setCapabilityId(capability.getId());
            row.setChannelId(capability.getChannelId());
            row.setChannelCode(capability.getChannelCode());
            row.setCurrencyCode(currency);
            row.setCurrencyStatus(ENABLED);
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(row.getCreateTime());
            row.setDeleted(NOT_DELETED);
            capabilityCurrencyMapper.insert(row);
        }
    }

    private void replaceCapabilityCardBrands(ChannelPaymentCapabilityDO capability, List<String> cardBrands) {
        capabilityCardBrandMapper.selectList(Wrappers.<ChannelCapabilityCardBrandDO>lambdaQuery()
                        .eq(ChannelCapabilityCardBrandDO::getDeleted, NOT_DELETED)
                        .eq(ChannelCapabilityCardBrandDO::getCapabilityId, capability.getId()))
                .forEach(row -> {
                    row.setDeleted(row.getId());
                    row.setUpdateTime(LocalDateTime.now());
                    capabilityCardBrandMapper.updateById(row);
                });
        int sort = 1;
        for (String brand : normalizeCodes(cardBrands)) {
            ChannelCapabilityCardBrandDO row = new ChannelCapabilityCardBrandDO();
            row.setCapabilityId(capability.getId());
            row.setChannelId(capability.getChannelId());
            row.setChannelCode(capability.getChannelCode());
            row.setCardBrand(brand);
            row.setBrandStatus(ENABLED);
            row.setSortOrder(sort++);
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(row.getCreateTime());
            row.setDeleted(NOT_DELETED);
            capabilityCardBrandMapper.insert(row);
        }
    }

    private void softDeleteCapabilityChildren(Long capabilityId) {
        capabilityCurrencyMapper.selectList(Wrappers.<ChannelCapabilityCurrencyDO>lambdaQuery()
                        .eq(ChannelCapabilityCurrencyDO::getDeleted, NOT_DELETED)
                        .eq(ChannelCapabilityCurrencyDO::getCapabilityId, capabilityId))
                .forEach(row -> {
                    row.setDeleted(row.getId());
                    row.setUpdateTime(LocalDateTime.now());
                    capabilityCurrencyMapper.updateById(row);
                });
        capabilityCardBrandMapper.selectList(Wrappers.<ChannelCapabilityCardBrandDO>lambdaQuery()
                        .eq(ChannelCapabilityCardBrandDO::getDeleted, NOT_DELETED)
                        .eq(ChannelCapabilityCardBrandDO::getCapabilityId, capabilityId))
                .forEach(row -> {
                    row.setDeleted(row.getId());
                    row.setUpdateTime(LocalDateTime.now());
                    capabilityCardBrandMapper.updateById(row);
                });
    }

    private List<String> listCapabilityCurrencies(Long capabilityId) {
        return capabilityCurrencyMapper.selectList(Wrappers.<ChannelCapabilityCurrencyDO>lambdaQuery()
                        .eq(ChannelCapabilityCurrencyDO::getDeleted, NOT_DELETED)
                        .eq(ChannelCapabilityCurrencyDO::getCapabilityId, capabilityId)
                        .orderByAsc(ChannelCapabilityCurrencyDO::getCurrencyCode))
                .stream().map(ChannelCapabilityCurrencyDO::getCurrencyCode).toList();
    }

    private List<String> listCapabilityCardBrands(Long capabilityId) {
        return capabilityCardBrandMapper.selectList(Wrappers.<ChannelCapabilityCardBrandDO>lambdaQuery()
                        .eq(ChannelCapabilityCardBrandDO::getDeleted, NOT_DELETED)
                        .eq(ChannelCapabilityCardBrandDO::getCapabilityId, capabilityId)
                        .orderByAsc(ChannelCapabilityCardBrandDO::getSortOrder))
                .stream().map(ChannelCapabilityCardBrandDO::getCardBrand).toList();
    }

    private Map<String, List<String>> capabilityMethods(Long channelId) {
        return capabilityMapper.selectList(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                        .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                        .eq(ChannelPaymentCapabilityDO::getChannelId, channelId)
                        .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED))
                .stream()
                .collect(Collectors.groupingBy(
                        ChannelPaymentCapabilityDO::getBusinessType,
                        LinkedHashMap::new,
                        Collectors.mapping(ChannelPaymentCapabilityDO::getPaymentMethod,
                                Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new))
                ));
    }

    private Map<Long, ChannelInfoDO> channelMap(List<Long> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return Map.of();
        }
        return channelInfoMapper.selectBatchIds(channelIds).stream()
                .collect(Collectors.toMap(ChannelInfoDO::getId, row -> row, (left, right) -> left));
    }

    private ChannelInfoDO findChannel(Long id) {
        ChannelInfoDO entity = channelInfoMapper.selectOne(Wrappers.<ChannelInfoDO>lambdaQuery()
                .eq(ChannelInfoDO::getDeleted, NOT_DELETED)
                .eq(ChannelInfoDO::getId, id));
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "渠道不存在");
        }
        return entity;
    }

    private ChannelPaymentCapabilityDO findCapability(Long id) {
        ChannelPaymentCapabilityDO entity = capabilityMapper.selectOne(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getId, id));
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "渠道支付能力不存在");
        }
        return entity;
    }

    private ChannelLimitRuleDO findLimit(Long id) {
        ChannelLimitRuleDO entity = limitRuleMapper.selectOne(Wrappers.<ChannelLimitRuleDO>lambdaQuery()
                .eq(ChannelLimitRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelLimitRuleDO::getId, id));
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "渠道限额不存在");
        }
        return entity;
    }

    private ChannelAccessConfigDO findAccess(Long id) {
        ChannelAccessConfigDO entity = accessConfigMapper.selectOne(Wrappers.<ChannelAccessConfigDO>lambdaQuery()
                .eq(ChannelAccessConfigDO::getDeleted, NOT_DELETED)
                .eq(ChannelAccessConfigDO::getId, id));
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "渠道接入配置不存在");
        }
        return entity;
    }

    private boolean hasActiveCapability(Long channelId) {
        return capabilityMapper.selectCount(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, channelId)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED)) > 0;
    }

    private boolean hasActiveLimit(Long channelId) {
        return limitRuleMapper.selectCount(Wrappers.<ChannelLimitRuleDO>lambdaQuery()
                .eq(ChannelLimitRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelLimitRuleDO::getChannelId, channelId)
                .eq(ChannelLimitRuleDO::getRuleStatus, ENABLED)) > 0;
    }

    private boolean hasActiveAccessConfig(Long channelId) {
        return accessConfigMapper.selectCount(Wrappers.<ChannelAccessConfigDO>lambdaQuery()
                .eq(ChannelAccessConfigDO::getDeleted, NOT_DELETED)
                .eq(ChannelAccessConfigDO::getChannelId, channelId)
                .eq(ChannelAccessConfigDO::getConfigStatus, ENABLED)) > 0;
    }

    private void validateChannelSupportsBusiness(ChannelInfoDO channel, String businessType) {
        if (BUSINESS_ACQUIRING.equals(businessType) && defaultZero(channel.getSupportAcquiring()) != ENABLED) {
            throw badRequest("渠道未开启收单能力");
        }
        if (BUSINESS_PAYOUT.equals(businessType) && defaultZero(channel.getSupportPayout()) != ENABLED) {
            throw badRequest("渠道未开启代付能力");
        }
        if (defaultZero(channel.getChannelStatus()) != ENABLED) {
            throw badRequest("渠道停用时不能启用能力或规则");
        }
    }

    private void validatePaymentMethod(String businessType, String paymentMethod) {
        if (BUSINESS_ACQUIRING.equals(businessType)) {
            assertDictValue("acquiring_payment_method", paymentMethod, true);
        } else {
            assertDictValue("payout_payment_method", paymentMethod, true);
        }
    }

    private void assertDictValue(String dictType, String value, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) {
                throw badRequest(dictType + "不能为空");
            }
            return;
        }
        if ("iso_currency".equals(dictType)) {
            return;
        }
        Long count = dictDataMapper.selectCount(Wrappers.<SysDictDataDO>lambdaQuery()
                .eq(SysDictDataDO::getDeleted, NOT_DELETED)
                .eq(SysDictDataDO::getStatus, ENABLED)
                .eq(SysDictDataDO::getDictType, dictType)
                .eq(SysDictDataDO::getDictValue, normalizeCode(value)));
        if (count <= 0) {
            throw badRequest("字典值不存在或未启用：" + dictType + "/" + value);
        }
    }

    private void validateBusinessType(String businessType) {
        if (!BUSINESS_ACQUIRING.equals(businessType) && !BUSINESS_PAYOUT.equals(businessType)) {
            throw badRequest("业务类型必须为 ACQUIRING 或 PAYOUT");
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != ENABLED && status != DISABLED)) {
            throw badRequest("状态必须为0停用或1启用");
        }
    }

    private String normalizeTransactionType(String businessType, String transactionType) {
        if (BUSINESS_PAYOUT.equals(businessType)) {
            return NONE;
        }
        return normalizeCode(transactionType);
    }

    private List<String> normalizeTransactionTypes(String businessType, List<String> transactionTypes, String transactionType) {
        if (BUSINESS_PAYOUT.equals(businessType)) {
            return List.of(NONE);
        }
        List<String> values = new ArrayList<>();
        if (transactionTypes != null) {
            values.addAll(transactionTypes);
        }
        if (values.isEmpty() && StringUtils.hasText(transactionType)) {
            values.addAll(splitTransactionTypes(businessType, transactionType));
        }
        return normalizeCodes(values);
    }

    private String joinTransactionTypes(List<String> transactionTypes) {
        return String.join(TRANSACTION_TYPE_SEPARATOR, transactionTypes);
    }

    private List<String> splitTransactionTypes(String businessType, String transactionType) {
        if (BUSINESS_PAYOUT.equals(businessType)) {
            return List.of(NONE);
        }
        if (!StringUtils.hasText(transactionType)) {
            return List.of();
        }
        return normalizeCodes(List.of(transactionType.split(TRANSACTION_TYPE_SEPARATOR)));
    }

    private String defaultScope(String value) {
        return StringUtils.hasText(value) ? normalizeCode(value) : ALL;
    }

    private String normalizeCode(String value) {
        return trim(value).toUpperCase();
    }

    private List<String> normalizeCodes(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .filter(Objects::nonNull)
                .map(this::normalizeCode)
                .distinct()
                .toList();
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String channelName(ChannelInfoDO channel) {
        if (channel == null) {
            return "";
        }
        return StringUtils.hasText(channel.getChannelCnName()) ? channel.getChannelCnName() : channel.getChannelEnName();
    }

    private String cipher(String value) {
        return Base64.getEncoder().encodeToString(trim(value).getBytes(StandardCharsets.UTF_8));
    }

    private String plain(String cipher) {
        if (!StringUtils.hasText(cipher)) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(cipher), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private String maskCipher(String cipher) {
        String value = plain(cipher);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        int prefix = Math.min(4, value.length() / 2);
        int suffix = Math.min(4, value.length() - prefix);
        return value.substring(0, prefix) + "********" + value.substring(value.length() - suffix);
    }

    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
