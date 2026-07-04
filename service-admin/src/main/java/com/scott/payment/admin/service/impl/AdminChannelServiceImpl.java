package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilitySaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelOption;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitBatchSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitSaveRequest;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCardBrandDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCurrencyDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelLimitRuleDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelPaymentCapabilityDO;
import com.scott.payment.admin.mapper.ChannelCapabilityCardBrandMapper;
import com.scott.payment.admin.mapper.ChannelCapabilityCurrencyMapper;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.admin.mapper.ChannelLimitRuleMapper;
import com.scott.payment.admin.mapper.ChannelPaymentCapabilityMapper;
import com.scott.payment.admin.mapper.SysDictDataMapper;
import com.scott.payment.admin.service.AdminChannelService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * <p>该服务只维护渠道基础数据、能力和限额，不执行真实渠道调用，也不承载支付交易状态机。</p>
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
    private static final String LIMIT_DAILY = "DAILY";
    private static final String LIMIT_WEEKLY = "WEEKLY";
    private static final String LIMIT_MONTHLY = "MONTHLY";
    private static final BigDecimal MIN_LIMIT_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal WEEKLY_LIMIT_MULTIPLIER = new BigDecimal("7");
    private static final BigDecimal MONTHLY_LIMIT_MULTIPLIER = new BigDecimal("4");
    private static final String TRANSACTION_TYPE_SEPARATOR = ",";
    private static final Pattern CHANNEL_CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,64}$");
    private static final Pattern HTTP_URL_PATTERN = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> INCREMENTAL_TRANSACTION_TYPES = Set.of("AUTHORIZATION", "PRE_AUTHORIZATION");

    private final ChannelInfoMapper channelInfoMapper;
    private final ChannelPaymentCapabilityMapper capabilityMapper;
    private final ChannelCapabilityCurrencyMapper capabilityCurrencyMapper;
    private final ChannelCapabilityCardBrandMapper capabilityCardBrandMapper;
    private final ChannelLimitRuleMapper limitRuleMapper;
    private final SysDictDataMapper dictDataMapper;

    public AdminChannelServiceImpl(ChannelInfoMapper channelInfoMapper,
                                   ChannelPaymentCapabilityMapper capabilityMapper,
                                   ChannelCapabilityCurrencyMapper capabilityCurrencyMapper,
                                   ChannelCapabilityCardBrandMapper capabilityCardBrandMapper,
                                   ChannelLimitRuleMapper limitRuleMapper,
                                   SysDictDataMapper dictDataMapper) {
        this.channelInfoMapper = channelInfoMapper;
        this.capabilityMapper = capabilityMapper;
        this.capabilityCurrencyMapper = capabilityCurrencyMapper;
        this.capabilityCardBrandMapper = capabilityCardBrandMapper;
        this.limitRuleMapper = limitRuleMapper;
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
                    option.setSupport3ds(channel.getSupport3ds());
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
        if (hasActiveCapability(id) || hasActiveLimit(id)) {
            throw badRequest("渠道存在启用中的能力或限额，不能删除");
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
            ChannelInfoDO channel = findChannel(entity.getChannelId());
            if (support3ds == ENABLED && !channelSupports3ds(channel, entity.getBusinessType())) {
                throw badRequest("渠道未开启收单3DS能力，不能开启支付能力3DS");
            }
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
    public List<LimitResponse> createLimits(LimitBatchSaveRequest request) {
        List<LimitSaveRequest> items = validateLimitBatchItems(request);
        validateLimitBatchAmountRelations(items);
        List<LimitResponse> responses = new ArrayList<>();
        for (LimitSaveRequest item : items) {
            responses.add(createLimit(item));
        }
        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LimitResponse> saveLimitDimension(LimitBatchSaveRequest request) {
        List<LimitSaveRequest> items = validateLimitBatchItems(request);
        validateSameLimitDimension(items);
        validateLimitBatchAmountRelations(items);
        List<LimitResponse> responses = new ArrayList<>();
        for (LimitSaveRequest item : items) {
            ChannelLimitRuleDO existing = findLimitByScope(item);
            if (existing == null) {
                responses.add(createLimit(item));
            } else {
                responses.add(updateLimit(existing.getId(), item));
            }
        }
        return responses;
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
        entity.setUpdateBy(currentOperatorName());
        entity.setUpdateTime(LocalDateTime.now());
        limitRuleMapper.updateById(entity);
        return toLimitResponse(entity, findChannel(entity.getChannelId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLimit(Long id) {
        ChannelLimitRuleDO entity = findLimit(id);
        entity.setDeleted(entity.getId());
        entity.setUpdateBy(currentOperatorName());
        entity.setUpdateTime(LocalDateTime.now());
        limitRuleMapper.updateById(entity);
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
        if (defaultZero(request.getSupportAcquiring()) != ENABLED) {
            request.setSupport3ds(DISABLED);
        }
        Long count = channelInfoMapper.selectCount(Wrappers.<ChannelInfoDO>lambdaQuery()
                .eq(ChannelInfoDO::getDeleted, NOT_DELETED)
                .eq(ChannelInfoDO::getChannelCode, code)
                .ne(id != null, ChannelInfoDO::getId, id));
        if (count > 0) {
            throw badRequest("渠道编码已存在");
        }
        if (StringUtils.hasText(request.getDefaultRequestUrl())
                && !HTTP_URL_PATTERN.matcher(trim(request.getDefaultRequestUrl())).matches()) {
            throw badRequest("默认请求地址必须以 http:// 或 https:// 开头");
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
        if (!channelSupports3ds(channel, businessType)) {
            request.setSupport3ds(DISABLED);
        } else if (defaultZero(request.getSupport3ds()) == ENABLED) {
            validateStatus(request.getSupport3ds());
        }
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
        String cardBrand = defaultScope(request.getCardBrand());
        String limitType = normalizeCode(request.getLimitType());
        validateChannelSupportsLimitBusiness(channel, businessType, request.getRuleStatus());
        validateLimitPaymentScope(request.getChannelId(), businessType, paymentMethod, cardBrand);
        assertDictValue("channel_limit_type", limitType, true);
        if (request.getLimitAmount() == null || request.getLimitAmount().compareTo(MIN_LIMIT_AMOUNT) < 0) {
            throw badRequest("限额金额必须大于等于0.01");
        }
        validateStatus(request.getRuleStatus());
        Long count = limitRuleMapper.selectCount(Wrappers.<ChannelLimitRuleDO>lambdaQuery()
                .eq(ChannelLimitRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelLimitRuleDO::getChannelId, request.getChannelId())
                .eq(ChannelLimitRuleDO::getBusinessType, businessType)
                .eq(ChannelLimitRuleDO::getPaymentMethod, paymentMethod)
                .eq(ChannelLimitRuleDO::getCardBrand, cardBrand)
                .eq(ChannelLimitRuleDO::getLimitType, limitType)
                .ne(id != null, ChannelLimitRuleDO::getId, id));
        if (count > 0) {
            throw badRequest("同一渠道、业务类型、支付方式/卡品牌和限额类型不能重复");
        }
        request.setBusinessType(businessType);
        request.setPaymentMethod(paymentMethod);
        request.setCardBrand(cardBrand);
        request.setLimitType(limitType);
        validateLimitAmountRelations(request, id);
        return channel;
    }

    private List<LimitSaveRequest> validateLimitBatchItems(LimitBatchSaveRequest request) {
        List<LimitSaveRequest> items = request == null ? List.of() : request.getItems();
        if (items == null || items.isEmpty()) {
            throw badRequest("限额规则不能为空");
        }
        return items;
    }

    private void validateSameLimitDimension(List<LimitSaveRequest> items) {
        LimitSaveRequest first = items.get(0);
        String businessType = normalizeCode(first.getBusinessType());
        String paymentMethod = defaultScope(first.getPaymentMethod());
        String cardBrand = defaultScope(first.getCardBrand());
        for (LimitSaveRequest item : items) {
            if (!Objects.equals(first.getChannelId(), item.getChannelId())
                    || !businessType.equals(normalizeCode(item.getBusinessType()))
                    || !paymentMethod.equals(defaultScope(item.getPaymentMethod()))
                    || !cardBrand.equals(defaultScope(item.getCardBrand()))) {
                throw badRequest("维度编辑只能提交同一渠道、业务类型、支付方式和卡品牌下的限额");
            }
        }
    }

    private void validateLimitBatchAmountRelations(List<LimitSaveRequest> items) {
        Map<LimitScope, Map<String, BigDecimal>> limitAmountsByScope = new LinkedHashMap<>();
        for (LimitSaveRequest item : items) {
            LimitScope scope = limitScope(item);
            Map<String, BigDecimal> amounts = limitAmountsByScope.computeIfAbsent(
                    scope,
                    currentScope -> existingLimitAmounts(currentScope, null)
            );
            if (StringUtils.hasText(item.getLimitType()) && item.getLimitAmount() != null) {
                amounts.put(normalizeCode(item.getLimitType()), item.getLimitAmount());
            }
        }
        limitAmountsByScope.values().forEach(this::validateLimitAmountRelations);
    }

    private void validateLimitAmountRelations(LimitSaveRequest request, Long id) {
        Map<String, BigDecimal> amounts = existingLimitAmounts(limitScope(request), id);
        amounts.put(request.getLimitType(), request.getLimitAmount());
        validateLimitAmountRelations(amounts);
    }

    private void validateLimitAmountRelations(Map<String, BigDecimal> amounts) {
        BigDecimal daily = amounts.get(LIMIT_DAILY);
        BigDecimal weekly = amounts.get(LIMIT_WEEKLY);
        BigDecimal monthly = amounts.get(LIMIT_MONTHLY);
        if (daily != null && weekly != null && weekly.compareTo(daily.multiply(WEEKLY_LIMIT_MULTIPLIER)) > 0) {
            throw badRequest("周限额不能超过日限额的7倍");
        }
        if (weekly != null && monthly != null && monthly.compareTo(weekly.multiply(MONTHLY_LIMIT_MULTIPLIER)) > 0) {
            throw badRequest("月限额不能超过周限额的4倍");
        }
    }

    private Map<String, BigDecimal> existingLimitAmounts(LimitScope scope, Long excludeId) {
        if (scope.channelId() == null || !StringUtils.hasText(scope.businessType())) {
            return new LinkedHashMap<>();
        }
        List<ChannelLimitRuleDO> rows = limitRuleMapper.selectList(Wrappers.<ChannelLimitRuleDO>lambdaQuery()
                .eq(ChannelLimitRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelLimitRuleDO::getChannelId, scope.channelId())
                .eq(ChannelLimitRuleDO::getBusinessType, scope.businessType())
                .eq(ChannelLimitRuleDO::getPaymentMethod, scope.paymentMethod())
                .eq(ChannelLimitRuleDO::getCardBrand, scope.cardBrand())
                .ne(excludeId != null, ChannelLimitRuleDO::getId, excludeId));
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        if (rows == null) {
            return amounts;
        }
        for (ChannelLimitRuleDO row : rows) {
            if (StringUtils.hasText(row.getLimitType()) && row.getLimitAmount() != null) {
                amounts.put(row.getLimitType(), row.getLimitAmount());
            }
        }
        return amounts;
    }

    private LimitScope limitScope(LimitSaveRequest request) {
        return new LimitScope(
                request.getChannelId(),
                normalizeCode(request.getBusinessType()),
                defaultScope(request.getPaymentMethod()),
                defaultScope(request.getCardBrand())
        );
    }

    private ChannelLimitRuleDO findLimitByScope(LimitSaveRequest request) {
        return limitRuleMapper.selectOne(Wrappers.<ChannelLimitRuleDO>lambdaQuery()
                .eq(ChannelLimitRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelLimitRuleDO::getChannelId, request.getChannelId())
                .eq(ChannelLimitRuleDO::getBusinessType, normalizeCode(request.getBusinessType()))
                .eq(ChannelLimitRuleDO::getPaymentMethod, defaultScope(request.getPaymentMethod()))
                .eq(ChannelLimitRuleDO::getCardBrand, defaultScope(request.getCardBrand()))
                .eq(ChannelLimitRuleDO::getLimitType, normalizeCode(request.getLimitType())));
    }

    private void validateChannelSupportsLimitBusiness(ChannelInfoDO channel, String businessType, Integer ruleStatus) {
        if (BUSINESS_ACQUIRING.equals(businessType) && defaultZero(channel.getSupportAcquiring()) != ENABLED) {
            throw badRequest("渠道未开启收单能力");
        }
        if (BUSINESS_PAYOUT.equals(businessType) && defaultZero(channel.getSupportPayout()) != ENABLED) {
            throw badRequest("渠道未开启代付能力");
        }
        if (defaultZero(ruleStatus) == ENABLED && defaultZero(channel.getChannelStatus()) != ENABLED) {
            throw badRequest("渠道停用时不能启用限额规则");
        }
    }

    private void validateLimitPaymentScope(Long channelId, String businessType, String paymentMethod, String cardBrand) {
        if (ALL.equals(paymentMethod)) {
            if (!ALL.equals(cardBrand)) {
                throw badRequest("渠道级限额不能绑定卡品牌");
            }
            if (!hasEnabledCapability(channelId, businessType, null)) {
                throw badRequest("该渠道业务类型下不存在启用中的支付能力");
            }
            return;
        }

        validatePaymentMethod(businessType, paymentMethod);
        ChannelPaymentCapabilityDO capability = findEnabledCapability(channelId, businessType, paymentMethod);
        if (capability == null) {
            throw badRequest("该渠道支付方式不存在启用中的支付能力");
        }
        if (!PAYMENT_BANK_CARD.equals(paymentMethod)) {
            if (!ALL.equals(cardBrand)) {
                throw badRequest("非银行卡支付方式不能绑定卡品牌");
            }
            return;
        }
        if (ALL.equals(cardBrand)) {
            return;
        }
        assertDictValue("card_brand", cardBrand, true);
        Long count = capabilityCardBrandMapper.selectCount(Wrappers.<ChannelCapabilityCardBrandDO>lambdaQuery()
                .eq(ChannelCapabilityCardBrandDO::getDeleted, NOT_DELETED)
                .eq(ChannelCapabilityCardBrandDO::getCapabilityId, capability.getId())
                .eq(ChannelCapabilityCardBrandDO::getCardBrand, cardBrand)
                .eq(ChannelCapabilityCardBrandDO::getBrandStatus, ENABLED));
        if (count <= 0) {
            throw badRequest("该银行卡支付能力未绑定启用的卡品牌：" + cardBrand);
        }
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
        String operatorName = currentOperatorName();
        entity.setChannelId(channel.getId());
        entity.setChannelCode(channel.getChannelCode());
        entity.setBusinessType(request.getBusinessType());
        entity.setPaymentMethod(request.getPaymentMethod());
        entity.setCardBrand(request.getCardBrand());
        entity.setLimitType(request.getLimitType());
        entity.setLimitCurrency(USD);
        entity.setLimitAmount(request.getLimitAmount());
        entity.setRuleStatus(request.getRuleStatus());
        entity.setRemark(trimToNull(request.getRemark()));
        if (!StringUtils.hasText(entity.getCreateBy())) {
            entity.setCreateBy(operatorName);
        }
        entity.setUpdateBy(operatorName);
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
        response.setCardBrand(entity.getCardBrand());
        response.setLimitType(entity.getLimitType());
        response.setLimitCurrency(entity.getLimitCurrency());
        response.setLimitAmount(entity.getLimitAmount());
        response.setRuleStatus(entity.getRuleStatus());
        response.setRemark(entity.getRemark());
        response.setCreateBy(entity.getCreateBy());
        response.setUpdateBy(entity.getUpdateBy());
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

    private boolean hasActiveCapability(Long channelId) {
        return capabilityMapper.selectCount(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, channelId)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED)) > 0;
    }

    private boolean hasEnabledCapability(Long channelId, String businessType, String paymentMethod) {
        return capabilityMapper.selectCount(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, channelId)
                .eq(ChannelPaymentCapabilityDO::getBusinessType, businessType)
                .eq(StringUtils.hasText(paymentMethod), ChannelPaymentCapabilityDO::getPaymentMethod, paymentMethod)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED)) > 0;
    }

    private ChannelPaymentCapabilityDO findEnabledCapability(Long channelId, String businessType, String paymentMethod) {
        return capabilityMapper.selectOne(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, channelId)
                .eq(ChannelPaymentCapabilityDO::getBusinessType, businessType)
                .eq(ChannelPaymentCapabilityDO::getPaymentMethod, paymentMethod)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED));
    }

    private boolean hasActiveLimit(Long channelId) {
        return limitRuleMapper.selectCount(Wrappers.<ChannelLimitRuleDO>lambdaQuery()
                .eq(ChannelLimitRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelLimitRuleDO::getChannelId, channelId)
                .eq(ChannelLimitRuleDO::getRuleStatus, ENABLED)) > 0;
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

    private boolean channelSupports3ds(ChannelInfoDO channel, String businessType) {
        return BUSINESS_ACQUIRING.equals(businessType)
                && defaultZero(channel.getSupportAcquiring()) == ENABLED
                && defaultZero(channel.getSupport3ds()) == ENABLED;
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

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "system";
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        if (StringUtils.hasText(account.getLoginAccount())) {
            return account.getLoginAccount();
        }
        return "system";
    }

    private record LimitScope(Long channelId, String businessType, String paymentMethod, String cardBrand) {
    }

    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
