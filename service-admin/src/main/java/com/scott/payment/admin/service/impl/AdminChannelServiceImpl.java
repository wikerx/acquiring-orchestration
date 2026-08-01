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
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMetadataSchemaItem;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelOption;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMidConfigQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMidConfigResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMidConfigSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitBatchSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MerchantChannelMidBindingQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MerchantChannelMidBindingResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MerchantChannelMidBindingSaveRequest;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCardBrandDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCurrencyDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelLimitRuleDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelMetadataSchemaDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelMidConfigDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelPaymentCapabilityDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.MerchantChannelMidBindingDO;
import com.scott.payment.admin.mapper.ChannelCapabilityCardBrandMapper;
import com.scott.payment.admin.mapper.ChannelCapabilityCurrencyMapper;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.admin.mapper.ChannelLimitRuleMapper;
import com.scott.payment.admin.mapper.ChannelMetadataSchemaMapper;
import com.scott.payment.admin.mapper.ChannelMidConfigMapper;
import com.scott.payment.admin.mapper.ChannelPaymentCapabilityMapper;
import com.scott.payment.admin.mapper.MerchantChannelMidBindingMapper;
import com.scott.payment.admin.mapper.SysDictDataMapper;
import com.scott.payment.admin.service.AdminChannelService;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
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


@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelServiceImpl
 * @date : 2026-07-03 16:10
 * @email : scott_x@163.com
 * @description : Admin Channel Service Impl 服务实现，位于 运营后台服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
public class AdminChannelServiceImpl implements AdminChannelService {

    /**
     * 软删除未删除标识。
     */
    private static final long NOT_DELETED = 0L;
    /**
     * 通用启用状态。
     */
    private static final int ENABLED = 1;
    /**
     * 通用停用状态。
     */
    private static final int DISABLED = 0;
    /**
     * 收单业务类型。
     */
    private static final String BUSINESS_ACQUIRING = "ACQUIRING";
    /**
     * 代付业务类型。
     */
    private static final String BUSINESS_PAYOUT = "PAYOUT";
    /**
     * 银行卡支付方式编码。
     */
    private static final String PAYMENT_BANK_CARD = "BANK_CARD";
    /**
     * 无卡品牌维度占位值。
     */
    private static final String NONE = "NONE";
    /**
     * 全部卡品牌维度占位值。
     */
    private static final String ALL = "ALL";
    /**
     * 脱敏元数据的占位值，编辑敏感字段留空或保持占位时不覆盖原始配置。
     */
    private static final String MASKED_METADATA_VALUE = "***";
    /**
     * 当前限额配置默认币种。
     */
    private static final String USD = "USD";
    /**
     * 日限额类型。
     */
    private static final String LIMIT_DAILY = "DAILY";
    /**
     * 周限额类型。
     */
    private static final String LIMIT_WEEKLY = "WEEKLY";
    /**
     * 月限额类型。
     */
    private static final String LIMIT_MONTHLY = "MONTHLY";
    /**
     * 限额配置允许的最小金额。
     */
    private static final BigDecimal MIN_LIMIT_AMOUNT = new BigDecimal("0.01");
    /**
     * 周限额默认按日限额 7 倍生成。
     */
    private static final BigDecimal WEEKLY_LIMIT_MULTIPLIER = new BigDecimal("7");
    /**
     * 月限额默认按周限额 4 倍生成。
     */
    private static final BigDecimal MONTHLY_LIMIT_MULTIPLIER = new BigDecimal("4");
    /**
     * 多交易类型保存时使用的分隔符。
     */
    private static final String TRANSACTION_TYPE_SEPARATOR = ",";
    /**
     * 渠道编码格式，限制为大写字母、数字和下划线。
     */
    private static final Pattern CHANNEL_CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,64}$");
    /**
     * MID 参数模板字段名格式。
     */
    private static final Pattern METADATA_FIELD_KEY_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{1,63}$");
    /**
     * 渠道默认请求地址格式。
     */
    private static final Pattern HTTP_URL_PATTERN = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);
    /**
     * 允许开启增量授权的交易类型。
     */
    private static final Set<String> INCREMENTAL_TRANSACTION_TYPES = Set.of("AUTHORIZATION", "PRE_AUTHORIZATION");
    /**
     * 后台支持配置的 MID 参数字段类型。
     */
    private static final Set<String> METADATA_FIELD_TYPES = Set.of(
            "TEXT", "PASSWORD", "URL", "NUMBER", "JSON", "TEXTAREA", "PRIVATE_KEY", "PUBLIC_KEY", "CERTIFICATE", "SELECT"
    );

    /**
     * 渠道基础信息数据访问对象。
     */
    private final ChannelInfoMapper channelInfoMapper;
    /**
     * 渠道 MID 参数模板数据访问对象。
     */
    private final ChannelMetadataSchemaMapper metadataSchemaMapper;
    /**
     * 渠道支付能力数据访问对象。
     */
    private final ChannelPaymentCapabilityMapper capabilityMapper;
    /**
     * 渠道能力支持币种数据访问对象。
     */
    private final ChannelCapabilityCurrencyMapper capabilityCurrencyMapper;
    /**
     * 渠道能力支持卡品牌数据访问对象。
     */
    private final ChannelCapabilityCardBrandMapper capabilityCardBrandMapper;
    /**
     * 渠道限额规则数据访问对象。
     */
    private final ChannelLimitRuleMapper limitRuleMapper;
    /**
     * 渠道真实 MID 配置数据访问对象。
     */
    private final ChannelMidConfigMapper midConfigMapper;
    /**
     * 商户与渠道 MID 绑定关系数据访问对象。
     */
    private final MerchantChannelMidBindingMapper midBindingMapper;
    /**
     * 系统字典数据访问对象，用于校验交易类型、支付方式、卡品牌等管理端配置值。
     */
    private final SysDictDataMapper dictDataMapper;

    /** 商户路由永久快照的事务型可靠失效协调器。 */
    private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;

/**
 * 整理admin渠道serviceimpl，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param channelInfoMapper channel Info Mapper 输入值，参与 渠道信息映射器 的查询、校验、转换、写入或日志摘要
 * @param metadataSchemaMapper metadata Schema Mapper 输入值，参与 metadataschema映射器 的查询、校验、转换、写入或日志摘要
 * @param capabilityMapper capability Mapper 输入值，参与 capability映射器 的查询、校验、转换、写入或日志摘要
 * @param capabilityCurrencyMapper 币种代码，格式为 ISO 4217 三位大写字母
 * @param capabilityCardBrandMapper 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
 * @param limitRuleMapper limit Rule Mapper 输入值，参与 limit规则映射器 的查询、校验、转换、写入或日志摘要
 * @param midConfigMapper MID Config Mapper 输入值，参与 mid配置映射器 的查询、校验、转换、写入或日志摘要
 * @param midBindingMapper MID Binding Mapper 输入值，参与 midbinding映射器 的查询、校验、转换、写入或日志摘要
 * @param dictDataMapper dict Data Mapper 输入值，参与 dictdata映射器 的查询、校验、转换、写入或日志摘要
 * @param cacheInvalidationCoordinator 商户路由永久快照可靠失效协调器
 */
    public AdminChannelServiceImpl(ChannelInfoMapper channelInfoMapper,
                                   ChannelMetadataSchemaMapper metadataSchemaMapper,
                                   ChannelPaymentCapabilityMapper capabilityMapper,
                                   ChannelCapabilityCurrencyMapper capabilityCurrencyMapper,
                                   ChannelCapabilityCardBrandMapper capabilityCardBrandMapper,
                                   ChannelLimitRuleMapper limitRuleMapper,
                                   ChannelMidConfigMapper midConfigMapper,
                                   MerchantChannelMidBindingMapper midBindingMapper,
                                   SysDictDataMapper dictDataMapper,
                                   ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator) {
        this.channelInfoMapper = channelInfoMapper;
        this.metadataSchemaMapper = metadataSchemaMapper;
        this.capabilityMapper = capabilityMapper;
        this.capabilityCurrencyMapper = capabilityCurrencyMapper;
        this.capabilityCardBrandMapper = capabilityCardBrandMapper;
        this.limitRuleMapper = limitRuleMapper;
        this.midConfigMapper = midConfigMapper;
        this.midBindingMapper = midBindingMapper;
        this.dictDataMapper = dictDataMapper;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
    }

    /**
     * 分页查询渠道基础信息。
     *
     * @param request 渠道状态、业务支持能力和关键字等筛选条件；为空时使用默认分页
     * @return 渠道基础信息分页结果
     */
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

    /**
     * 查询渠道下拉选项。
     *
     * @return 渠道编码、名称、状态和业务支持能力摘要
     */
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

    /**
     * 查询渠道基础信息详情。
     *
     * @param id 渠道主键
     * @return 渠道基础信息、支付方式摘要和 MID 参数模板
     */
    @Override
    public ChannelInfoResponse getChannel(Long id) {
        return toChannelResponse(findChannel(id));
    }

    /**
     * 新增渠道基础信息。
     *
     * @param request 渠道编码、名称、业务支持能力、默认请求地址和 MID 参数模板
     * @return 新增后的渠道基础信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelInfoResponse createChannel(ChannelInfoSaveRequest request) {
        validateChannelRequest(request, null);
        ChannelInfoDO entity = new ChannelInfoDO();
        fillChannel(entity, request, LocalDateTime.now());
        entity.setCreateTime(entity.getUpdateTime());
        entity.setDeleted(NOT_DELETED);
        channelInfoMapper.insert(entity);
        replaceMetadataSchemas(entity, request.getMetadataSchemas());
        return toChannelResponse(entity);
    }

    /**
     * 更新渠道基础信息，并同步替换 MID 参数模板。
     *
     * @param id 渠道主键
     * @param request 渠道基础信息和 MID 参数模板
     * @return 更新后的渠道基础信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelInfoResponse updateChannel(Long id, ChannelInfoSaveRequest request) {
        ChannelInfoDO entity = findChannel(id);
        validateChannelRequest(request, id);
        prepareRouteInvalidationByChannel(entity.getId());
        fillChannel(entity, request, LocalDateTime.now());
        channelInfoMapper.updateById(entity);
        replaceMetadataSchemas(entity, request.getMetadataSchemas());
        return toChannelResponse(entity);
    }

    /**
     * 更新渠道启停状态。
     *
     * @param id 渠道主键
     * @param status 目标状态：0停用，1启用
     * @return 更新后的渠道基础信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelInfoResponse updateChannelStatus(Long id, Integer status) {
        ChannelInfoDO entity = findChannel(id);
        validateStatus(status);
        prepareRouteInvalidationByChannel(entity.getId());
        entity.setChannelStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        channelInfoMapper.updateById(entity);
        return toChannelResponse(entity);
    }

    /**
     * 软删除渠道基础信息。
     * <p>
     * 渠道存在启用中的支付能力或限额规则时禁止删除，避免后台配置出现悬挂引用。
     *
     * @param id 渠道主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChannel(Long id) {
        ChannelInfoDO entity = findChannel(id);
        if (hasActiveCapability(id) || hasActiveLimit(id)) {
            throw badRequest("渠道存在启用中的能力或限额，不能删除");
        }
        prepareRouteInvalidationByChannel(entity.getId());
        entity.setDeleted(entity.getId());
        entity.setUpdateTime(LocalDateTime.now());
        channelInfoMapper.updateById(entity);
    }

    /**
     * 分页查询渠道支付能力。
     *
     * @param request 渠道、业务类型、支付方式、交易类型和状态等筛选条件
     * @return 支付能力分页结果
     */
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

    /**
     * 查询渠道支付能力详情。
     *
     * @param id 支付能力主键
     * @return 支付能力详情，包含支持币种和卡品牌
     */
    @Override
    public CapabilityResponse getCapability(Long id) {
        ChannelPaymentCapabilityDO entity = findCapability(id);
        return toCapabilityResponse(entity, findChannel(entity.getChannelId()));
    }

    /**
     * 新增渠道支付能力。
     *
     * @param request 业务类型、支付方式、交易类型、币种、卡品牌和能力开关配置
     * @return 新增后的支付能力详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapabilityResponse createCapability(CapabilitySaveRequest request) {
        ChannelInfoDO channel = validateCapabilityRequest(request, null);
        prepareRouteInvalidationByChannel(channel.getId());
        ChannelPaymentCapabilityDO entity = new ChannelPaymentCapabilityDO();
        fillCapability(entity, request, channel, LocalDateTime.now());
        entity.setCreateTime(entity.getUpdateTime());
        entity.setDeleted(NOT_DELETED);
        capabilityMapper.insert(entity);
        replaceCapabilityCurrencies(entity, request.getCurrencyCodes());
        replaceCapabilityCardBrands(entity, request.getCardBrands());
        return toCapabilityResponse(entity, channel);
    }

    /**
     * 更新渠道支付能力，并同步替换支持币种和卡品牌范围。
     *
     * @param id 支付能力主键
     * @param request 支付能力配置
     * @return 更新后的支付能力详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapabilityResponse updateCapability(Long id, CapabilitySaveRequest request) {
        ChannelPaymentCapabilityDO entity = findCapability(id);
        ChannelInfoDO channel = validateCapabilityRequest(request, id);
        prepareRouteInvalidationByChannel(entity.getChannelId());
        if (!Objects.equals(entity.getChannelId(), channel.getId())) {
            prepareRouteInvalidationByChannel(channel.getId());
        }
        fillCapability(entity, request, channel, LocalDateTime.now());
        capabilityMapper.updateById(entity);
        replaceCapabilityCurrencies(entity, request.getCurrencyCodes());
        replaceCapabilityCardBrands(entity, request.getCardBrands());
        return toCapabilityResponse(entity, channel);
    }

    /**
     * 更新支付能力启停状态。
     *
     * @param id 支付能力主键
     * @param status 目标状态：0停用，1启用
     * @return 更新后的支付能力详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapabilityResponse updateCapabilityStatus(Long id, Integer status) {
        ChannelPaymentCapabilityDO entity = findCapability(id);
        validateStatus(status);
        if (status == ENABLED) {
            ChannelInfoDO channel = findChannel(entity.getChannelId());
            validateChannelSupportsBusiness(channel, entity.getBusinessType());
        }
        prepareRouteInvalidationByChannel(entity.getChannelId());
        entity.setCapabilityStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        capabilityMapper.updateById(entity);
        return toCapabilityResponse(entity, findChannel(entity.getChannelId()));
    }

    /**
     * 更新支付能力的 3DS 和增量授权开关。
     *
     * @param id 支付能力主键
     * @param support3ds 3DS 支持状态；为空表示不变
     * @param supportIncrementalAuthorization 增量授权支持状态；为空表示不变
     * @return 更新后的支付能力详情
     */
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
        prepareRouteInvalidationByChannel(entity.getChannelId());
        entity.setUpdateTime(LocalDateTime.now());
        capabilityMapper.updateById(entity);
        return toCapabilityResponse(entity, findChannel(entity.getChannelId()));
    }

    /**
     * 软删除渠道支付能力，并同步软删除能力下的币种和卡品牌配置。
     *
     * @param id 支付能力主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCapability(Long id) {
        ChannelPaymentCapabilityDO entity = findCapability(id);
        prepareRouteInvalidationByChannel(entity.getChannelId());
        entity.setDeleted(entity.getId());
        entity.setUpdateTime(LocalDateTime.now());
        capabilityMapper.updateById(entity);
        softDeleteCapabilityChildren(id);
    }

    /**
     * 分页查询渠道限额规则。
     *
     * @param request 渠道、业务类型、支付方式、卡品牌、限额类型和状态等筛选条件
     * @return 限额规则分页结果
     */
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

    /**
     * 查询渠道限额规则详情。
     *
     * @param id 限额规则主键
     * @return 限额规则详情
     */
    @Override
    public LimitResponse getLimit(Long id) {
        ChannelLimitRuleDO entity = findLimit(id);
        return toLimitResponse(entity, findChannel(entity.getChannelId()));
    }

    /**
     * 新增单条渠道限额规则。
     *
     * @param request 限额维度、限额类型、金额和状态
     * @return 新增后的限额规则详情
     */
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

    /**
     * 批量新增渠道限额规则。
     *
     * @param request 限额规则批量保存请求
     * @return 新增后的限额规则列表
     */
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

    /**
     * 保存同一限额维度下的多周期规则。
     *
     * @param request 同一渠道、业务类型、支付方式和卡品牌下的限额规则集合
     * @return 新增或更新后的限额规则列表
     */
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

    /**
     * 更新单条渠道限额规则。
     *
     * @param id 限额规则主键
     * @param request 限额维度、限额类型、金额和状态
     * @return 更新后的限额规则详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LimitResponse updateLimit(Long id, LimitSaveRequest request) {
        ChannelLimitRuleDO entity = findLimit(id);
        ChannelInfoDO channel = validateLimitRequest(request, id);
        fillLimit(entity, request, channel, LocalDateTime.now());
        limitRuleMapper.updateById(entity);
        return toLimitResponse(entity, channel);
    }

    /**
     * 更新限额规则启停状态。
     *
     * @param id 限额规则主键
     * @param status 目标状态：0停用，1启用
     * @return 更新后的限额规则详情
     */
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

    /**
     * 软删除渠道限额规则。
     *
     * @param id 限额规则主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLimit(Long id) {
        ChannelLimitRuleDO entity = findLimit(id);
        entity.setDeleted(entity.getId());
        entity.setUpdateBy(currentOperatorName());
        entity.setUpdateTime(LocalDateTime.now());
        limitRuleMapper.updateById(entity);
    }

    /**
     * 分页查询渠道 MID 配置。
     *
     * @param request 查询条件
     * @return 渠道 MID 分页结果
     */
    @Override
    public PageResult<ChannelMidConfigResponse> pageMids(ChannelMidConfigQuery request) {
        ChannelMidConfigQuery query = request == null ? new ChannelMidConfigQuery() : request;
        Page<ChannelMidConfigDO> page = midConfigMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildMidQuery(query)
        );
        Map<Long, ChannelInfoDO> channelMap = channelMap(page.getRecords().stream().map(ChannelMidConfigDO::getChannelId).toList());
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(row -> toMidResponse(row, channelMap.get(row.getChannelId()))).toList());
    }

    /**
     * 查询渠道 MID 配置详情。
     *
     * @param id MID 配置主键
     * @return MID 配置详情
     */
    @Override
    public ChannelMidConfigResponse getMid(Long id) {
        ChannelMidConfigDO entity = findMid(id);
        return toMidResponse(entity, findChannel(entity.getChannelId()));
    }

    /**
     * 新增渠道 MID 配置。
     *
     * @param request MID 配置参数
     * @return 新增后的 MID 配置
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelMidConfigResponse createMid(ChannelMidConfigSaveRequest request) {
        ChannelInfoDO channel = validateMidRequest(request, null);
        ChannelMidConfigDO entity = new ChannelMidConfigDO();
        fillMid(entity, request, channel, LocalDateTime.now());
        entity.setCreateTime(entity.getUpdateTime());
        entity.setDeleted(NOT_DELETED);
        midConfigMapper.insert(entity);
        return toMidResponse(entity, channel);
    }

    /**
     * 更新渠道 MID 配置。
     *
     * @param id MID 配置主键
     * @param request MID 配置参数
     * @return 更新后的 MID 配置
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelMidConfigResponse updateMid(Long id, ChannelMidConfigSaveRequest request) {
        ChannelMidConfigDO entity = findMid(id);
        mergeMetadataValuesForUpdate(entity, request);
        ChannelInfoDO channel = validateMidRequest(request, id);
        prepareRouteInvalidationByMid(entity.getId());
        fillMid(entity, request, channel, LocalDateTime.now());
        midConfigMapper.updateById(entity);
        refreshBindingChannelMid(entity);
        return toMidResponse(entity, channel);
    }

    /**
     * 更新渠道 MID 启停状态。
     *
     * @param id MID 配置主键
     * @param status 目标状态
     * @return 更新后的 MID 配置
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelMidConfigResponse updateMidStatus(Long id, Integer status) {
        validateStatus(status);
        ChannelMidConfigDO entity = findMid(id);
        if (status == ENABLED) {
            validateChannelSupportsBusiness(findChannel(entity.getChannelId()), entity.getBusinessType());
        }
        prepareRouteInvalidationByMid(entity.getId());
        entity.setMidStatus(status);
        entity.setUpdateBy(currentOperatorName());
        entity.setUpdateTime(LocalDateTime.now());
        midConfigMapper.updateById(entity);
        return toMidResponse(entity, findChannel(entity.getChannelId()));
    }

    /**
     * 软删除渠道 MID 配置。
     *
     * @param id MID 配置主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMid(Long id) {
        ChannelMidConfigDO entity = findMid(id);
        if (hasActiveMidBinding(id)) {
            throw badRequest("MID存在启用中的商户绑定，不能删除");
        }
        prepareRouteInvalidationByMid(entity.getId());
        entity.setDeleted(entity.getId());
        entity.setUpdateBy(currentOperatorName());
        entity.setUpdateTime(LocalDateTime.now());
        midConfigMapper.updateById(entity);
    }

    /**
     * 分页查询商户渠道 MID 绑定关系。
     *
     * @param request 查询条件
     * @return 绑定关系分页结果
     */
    @Override
    public PageResult<MerchantChannelMidBindingResponse> pageMidBindings(MerchantChannelMidBindingQuery request) {
        MerchantChannelMidBindingQuery query = request == null ? new MerchantChannelMidBindingQuery() : request;
        Page<MerchantChannelMidBindingDO> page = midBindingMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildMidBindingQuery(query)
        );
        Map<Long, ChannelInfoDO> channelMap = channelMap(page.getRecords().stream().map(MerchantChannelMidBindingDO::getChannelId).toList());
        Map<Long, ChannelMidConfigDO> midMap = midMap(page.getRecords().stream().map(MerchantChannelMidBindingDO::getMidConfigId).toList());
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(row -> toMidBindingResponse(row, channelMap.get(row.getChannelId()), midMap.get(row.getMidConfigId()))).toList());
    }

    /**
     * 查询商户渠道 MID 绑定详情。
     *
     * @param id 绑定主键
     * @return 绑定详情
     */
    @Override
    public MerchantChannelMidBindingResponse getMidBinding(Long id) {
        MerchantChannelMidBindingDO entity = findMidBinding(id);
        return toMidBindingResponse(entity, findChannel(entity.getChannelId()), findMid(entity.getMidConfigId()));
    }

    /**
     * 新增商户渠道 MID 绑定。
     *
     * @param request 绑定参数
     * @return 新增后的绑定关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantChannelMidBindingResponse createMidBinding(MerchantChannelMidBindingSaveRequest request) {
        ChannelMidConfigDO mid = validateMidBindingRequest(request, null);
        prepareRouteInvalidation(request.getMerchantId());
        MerchantChannelMidBindingDO entity = new MerchantChannelMidBindingDO();
        fillMidBinding(entity, request, mid, LocalDateTime.now());
        entity.setCreateTime(entity.getUpdateTime());
        entity.setDeleted(NOT_DELETED);
        midBindingMapper.insert(entity);
        return toMidBindingResponse(entity, findChannel(entity.getChannelId()), mid);
    }

    /**
     * 更新商户渠道 MID 绑定。
     *
     * @param id 绑定主键
     * @param request 绑定参数
     * @return 更新后的绑定关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantChannelMidBindingResponse updateMidBinding(Long id, MerchantChannelMidBindingSaveRequest request) {
        MerchantChannelMidBindingDO entity = findMidBinding(id);
        ChannelMidConfigDO mid = validateMidBindingRequest(request, id);
        prepareRouteInvalidation(entity.getMerchantId());
        prepareRouteInvalidation(request.getMerchantId());
        fillMidBinding(entity, request, mid, LocalDateTime.now());
        midBindingMapper.updateById(entity);
        return toMidBindingResponse(entity, findChannel(entity.getChannelId()), mid);
    }

    /**
     * 更新商户渠道 MID 绑定状态。
     *
     * @param id 绑定主键
     * @param status 目标状态
     * @return 更新后的绑定关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantChannelMidBindingResponse updateMidBindingStatus(Long id, Integer status) {
        validateStatus(status);
        MerchantChannelMidBindingDO entity = findMidBinding(id);
        if (status == ENABLED) {
            ChannelMidConfigDO mid = findMid(entity.getMidConfigId());
            if (defaultZero(mid.getMidStatus()) != ENABLED) {
                throw badRequest("MID停用时不能启用绑定");
            }
        }
        prepareRouteInvalidation(entity.getMerchantId());
        entity.setBindingStatus(status);
        entity.setUpdateBy(currentOperatorName());
        entity.setUpdateTime(LocalDateTime.now());
        midBindingMapper.updateById(entity);
        return toMidBindingResponse(entity, findChannel(entity.getChannelId()), findMid(entity.getMidConfigId()));
    }

    /**
     * 软删除商户渠道 MID 绑定。
     *
     * @param id 绑定主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMidBinding(Long id) {
        MerchantChannelMidBindingDO entity = findMidBinding(id);
        prepareRouteInvalidation(entity.getMerchantId());
        entity.setDeleted(entity.getId());
        entity.setUpdateBy(currentOperatorName());
        entity.setUpdateTime(LocalDateTime.now());
        midBindingMapper.updateById(entity);
    }

    /**
     * 校验并规范化渠道基础资料。
     *
     * <p>约束渠道编码唯一、能力和状态取值合法、请求地址使用 HTTP(S)、
     * 超时时间受控；不支持收单时强制关闭 3DS 能力。</p>
     *
     * @param request 渠道保存请求
     * @param id 更新场景的渠道主键，创建时为 {@code null}
     */
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
        validateTimeoutSeconds(request.getConnectTimeoutSeconds(), "连接超时时间");
        validateTimeoutSeconds(request.getReadTimeoutSeconds(), "读取超时时间");
        validateMetadataSchemas(request.getMetadataSchemas());
    }

    /**
     * 校验并规范化渠道 MID 配置及其交易范围。
     *
     * <p>校验渠道业务能力、支付方式、币种、结算周期、生效区间和元数据，
     * 同一渠道下的 MID 必须唯一；金额和结算币种不在此处做隐式转换。</p>
     *
     * @param request MID 保存请求
     * @param id 更新场景的 MID 主键，创建时为 {@code null}
     * @return MID 所属渠道
     */
    private ChannelInfoDO validateMidRequest(ChannelMidConfigSaveRequest request, Long id) {
        ChannelInfoDO channel = findChannel(request.getChannelId());
        String businessType = normalizeCode(request.getBusinessType());
        validateBusinessType(businessType);
        validateChannelSupportsBusiness(channel, businessType);
        validateStatus(request.getMidStatus());
        request.setBusinessType(businessType);
        request.setChannelMid(resolveChannelMid(request));
        request.setPaymentMethodScope(normalizeScope(request.getPaymentMethodScope(), "支付方式"));
        request.setTransactionTypeScope(resolveMidTransactionTypeScope(channel.getId(), businessType, request.getPaymentMethodScope()));
        request.setCardBrandScope(resolveMidCardBrandScope(channel.getId(), businessType, request.getPaymentMethodScope(), request.getCardBrandScope()));
        request.setCurrencyScope(normalizeScope(request.getCurrencyScope(), "交易币种范围"));
        request.setAllowedCountryScope(normalizeScope(request.getAllowedCountryScope(), "允许交易国家范围"));
        validatePaymentMethodScope(channel.getId(), businessType, request.getPaymentMethodScope());
        validateCurrencyScope(request.getCurrencyScope());
        request.setDefaultSettlementCurrency(normalizeCode(request.getDefaultSettlementCurrency()));
        assertDictValue("iso_currency", request.getDefaultSettlementCurrency(), true);
        request.setSettlementCycle(normalizeSettlementCycle(request.getSettlementCycle()));
        if (request.getEffectiveTime() != null && request.getExpireTime() != null
                && !request.getExpireTime().isAfter(request.getEffectiveTime())) {
            throw badRequest("MID失效时间必须晚于生效时间");
        }
        validateMetadataValues(channel.getId(), request.getMetadataValueJson());
        Long count = midConfigMapper.selectCount(Wrappers.<ChannelMidConfigDO>lambdaQuery()
                .eq(ChannelMidConfigDO::getDeleted, NOT_DELETED)
                .eq(ChannelMidConfigDO::getChannelId, channel.getId())
                .eq(ChannelMidConfigDO::getChannelMid, trim(request.getChannelMid()))
                .ne(id != null, ChannelMidConfigDO::getId, id));
        if (count > 0) {
            throw badRequest("同一渠道下 MID 不能重复");
        }
        return channel;
    }

    /**
     * 校验midbinding请求输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ChannelMidConfigDO validateMidBindingRequest(MerchantChannelMidBindingSaveRequest request, Long id) {
        validateStatus(request.getBindingStatus());
        if (request.getEffectiveTime() != null && request.getExpireTime() != null
                && !request.getExpireTime().isAfter(request.getEffectiveTime())) {
            throw badRequest("绑定失效时间必须晚于生效时间");
        }
        ChannelMidConfigDO mid = findMid(request.getMidConfigId());
        if (defaultZero(request.getBindingStatus()) == ENABLED && defaultZero(mid.getMidStatus()) != ENABLED) {
            throw badRequest("MID停用时不能启用绑定");
        }
        Long count = midBindingMapper.selectCount(Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED)
                .eq(MerchantChannelMidBindingDO::getMerchantId, trim(request.getMerchantId()))
                .eq(MerchantChannelMidBindingDO::getMidConfigId, request.getMidConfigId())
                .ne(id != null, MerchantChannelMidBindingDO::getId, id));
        if (count > 0) {
            throw badRequest("同一商户和 MID 不能重复绑定");
        }
        return mid;
    }

    /**
     * 校验metadataschemas输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param schemas schemas 输入值，参与 schemas 的查询、校验、转换、写入或日志摘要
     */
    private void validateMetadataSchemas(List<ChannelMetadataSchemaItem> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            return;
        }
        Set<String> fieldKeys = new LinkedHashSet<>();
        for (ChannelMetadataSchemaItem schema : schemas) {
            String fieldKey = trim(schema.getFieldKey());
            if (!METADATA_FIELD_KEY_PATTERN.matcher(fieldKey).matches()) {
                throw badRequest("渠道元数据 key 必须以字母开头，仅支持字母、数字、下划线，长度2-64");
            }
            if (!fieldKeys.add(fieldKey)) {
                throw badRequest("渠道元数据 key 不能重复：" + fieldKey);
            }
            if (!StringUtils.hasText(schema.getFieldLabel())) {
                throw badRequest("渠道元数据名称不能为空：" + fieldKey);
            }
            String fieldType = normalizeCode(schema.getFieldType());
            if (!METADATA_FIELD_TYPES.contains(fieldType)) {
                throw badRequest("渠道元数据类型不支持：" + fieldType);
            }
            validateStatus(defaultOne(schema.getRequiredFlag()));
            validateStatus(defaultZero(schema.getSensitiveFlag()));
            validateStatus(defaultOne(schema.getFieldStatus()));
            if (defaultZero(schema.getSensitiveFlag()) == ENABLED && StringUtils.hasText(schema.getDefaultValue())) {
                throw badRequest("敏感元数据字段不能配置默认值：" + fieldKey);
            }
            if (StringUtils.hasText(schema.getValidationRegex())) {
                try {
                    Pattern.compile(schema.getValidationRegex());
                } catch (Exception exception) {
                    throw badRequest("渠道元数据正则表达式不合法：" + fieldKey);
                }
            }
        }
    }

    /**
     * 校验capability请求输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 校验limit请求输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 校验limitbatchitems输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private List<LimitSaveRequest> validateLimitBatchItems(LimitBatchSaveRequest request) {
        List<LimitSaveRequest> items = request == null ? List.of() : request.getItems();
        if (items == null || items.isEmpty()) {
            throw badRequest("限额规则不能为空");
        }
        return items;
    }

    /**
     * 校验samelimitdimension输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param items items 输入值，参与 items 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 校验limitbatch金额relations输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param items items 输入值，参与 items 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 校验limit金额relations输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    private void validateLimitAmountRelations(LimitSaveRequest request, Long id) {
        Map<String, BigDecimal> amounts = existingLimitAmounts(limitScope(request), id);
        amounts.put(request.getLimitType(), request.getLimitAmount());
        validateLimitAmountRelations(amounts);
    }

    /**
     * 校验limit金额relations输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param amounts 金额值，单位必须结合 currency 或同名币种字段解释
     */
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

    /**
     * 规范化existinglimitamounts，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param scope scope 输入值，参与 scope 的查询、校验、转换、写入或日志摘要
     * @param excludeId exclude ID 输入值，参与 excludeID 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 规范化limitscope，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LimitScope limitScope(LimitSaveRequest request) {
        return new LimitScope(
                request.getChannelId(),
                normalizeCode(request.getBusinessType()),
                defaultScope(request.getPaymentMethod()),
                defaultScope(request.getCardBrand())
        );
    }

    /**
     * 查询limitbyscope，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private ChannelLimitRuleDO findLimitByScope(LimitSaveRequest request) {
        return limitRuleMapper.selectOne(Wrappers.<ChannelLimitRuleDO>lambdaQuery()
                .eq(ChannelLimitRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelLimitRuleDO::getChannelId, request.getChannelId())
                .eq(ChannelLimitRuleDO::getBusinessType, normalizeCode(request.getBusinessType()))
                .eq(ChannelLimitRuleDO::getPaymentMethod, defaultScope(request.getPaymentMethod()))
                .eq(ChannelLimitRuleDO::getCardBrand, defaultScope(request.getCardBrand()))
                .eq(ChannelLimitRuleDO::getLimitType, normalizeCode(request.getLimitType())));
    }

    /**
     * 校验渠道supportslimitbusiness输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param ruleStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     */
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

    /**
     * 校验limitpaymentscope输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param paymentMethod payment Method 输入值，参与 paymentmethod 的查询、校验、转换、写入或日志摘要
     * @param cardBrand 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
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

    /**
     * 构造渠道对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
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
        entity.setConnectTimeoutSeconds(defaultTimeout(request.getConnectTimeoutSeconds(), 10));
        entity.setReadTimeoutSeconds(defaultTimeout(request.getReadTimeoutSeconds(), 30));
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setUpdateTime(now);
    }

    /**
     * 构造渠道 MID对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
    private void fillMid(ChannelMidConfigDO entity, ChannelMidConfigSaveRequest request, ChannelInfoDO channel, LocalDateTime now) {
        String operatorName = currentOperatorName();
        entity.setChannelId(channel.getId());
        entity.setChannelCode(channel.getChannelCode());
        entity.setChannelMid(trim(request.getChannelMid()));
        entity.setMidName(StringUtils.hasText(request.getMidName()) ? trim(request.getMidName()) : trim(request.getChannelMid()));
        entity.setTerminalId(trimToNull(request.getTerminalId()));
        entity.setBusinessType(request.getBusinessType());
        entity.setPaymentMethodScope(request.getPaymentMethodScope());
        entity.setCardBrandScope(request.getCardBrandScope());
        entity.setTransactionTypeScope(request.getTransactionTypeScope());
        entity.setCurrencyScope(request.getCurrencyScope());
        entity.setAllowedCountryScope(request.getAllowedCountryScope());
        entity.setDefaultSettlementCurrency(request.getDefaultSettlementCurrency());
        entity.setSettlementCycle(request.getSettlementCycle());
        entity.setSettlementCutoffTime(request.getSettlementCutoffTime());
        entity.setSettlementTimeZone(trim(request.getSettlementTimeZone()));
        entity.setMcc(trimToNull(request.getMcc()));
        entity.setStatementDescriptor(trimToNull(request.getStatementDescriptor()));
        entity.setMetadataValueJson(trimToNull(request.getMetadataValueJson()));
        entity.setMidStatus(request.getMidStatus());
        entity.setEffectiveTime(request.getEffectiveTime());
        entity.setExpireTime(request.getExpireTime());
        entity.setRemark(trimToNull(request.getRemark()));
        if (!StringUtils.hasText(entity.getCreateBy())) {
            entity.setCreateBy(operatorName);
        }
        entity.setUpdateBy(operatorName);
        entity.setUpdateTime(now);
    }

/**
 * 构造渠道 MID 绑定对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param mid MID 输入值，参与 渠道 MID 的查询、校验、转换、写入或日志摘要
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 */
    private void fillMidBinding(MerchantChannelMidBindingDO entity, MerchantChannelMidBindingSaveRequest request,
                                ChannelMidConfigDO mid, LocalDateTime now) {
        String operatorName = currentOperatorName();
        entity.setMerchantId(trim(request.getMerchantId()));
        entity.setChannelId(mid.getChannelId());
        entity.setChannelCode(mid.getChannelCode());
        entity.setMidConfigId(mid.getId());
        entity.setChannelMid(mid.getChannelMid());
        entity.setBindingStatus(request.getBindingStatus());
        entity.setEffectiveTime(request.getEffectiveTime());
        entity.setExpireTime(request.getExpireTime());
        entity.setRemark(trimToNull(request.getRemark()));
        if (!StringUtils.hasText(entity.getCreateBy())) {
            entity.setCreateBy(operatorName);
        }
        entity.setUpdateBy(operatorName);
        entity.setUpdateTime(now);
    }

/**
 * 构造渠道能力对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 */
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

    /**
     * 构造限额对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 构造渠道响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
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
        response.setConnectTimeoutSeconds(entity.getConnectTimeoutSeconds());
        response.setReadTimeoutSeconds(entity.getReadTimeoutSeconds());
        response.setSortOrder(entity.getSortOrder());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        Map<String, List<String>> methods = capabilityMethods(entity.getId());
        response.setAcquiringPaymentMethods(methods.getOrDefault(BUSINESS_ACQUIRING, List.of()));
        response.setPayoutPaymentMethods(methods.getOrDefault(BUSINESS_PAYOUT, List.of()));
        response.setMetadataSchemas(listMetadataSchemas(entity.getId()));
        return response;
    }

    /**
     * 构造mid响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private ChannelMidConfigResponse toMidResponse(ChannelMidConfigDO entity, ChannelInfoDO channel) {
        ChannelMidConfigResponse response = new ChannelMidConfigResponse();
        response.setId(entity.getId());
        response.setChannelId(entity.getChannelId());
        response.setChannelCode(entity.getChannelCode());
        response.setChannelName(channelName(channel));
        response.setChannelMid(entity.getChannelMid());
        response.setMidName(entity.getMidName());
        response.setTerminalId(entity.getTerminalId());
        response.setBusinessType(entity.getBusinessType());
        response.setPaymentMethodScope(entity.getPaymentMethodScope());
        response.setCardBrandScope(entity.getCardBrandScope());
        response.setTransactionTypeScope(entity.getTransactionTypeScope());
        response.setCurrencyScope(entity.getCurrencyScope());
        response.setAllowedCountryScope(entity.getAllowedCountryScope());
        response.setDefaultSettlementCurrency(entity.getDefaultSettlementCurrency());
        response.setSettlementCycle(entity.getSettlementCycle());
        response.setSettlementCutoffTime(entity.getSettlementCutoffTime());
        response.setSettlementTimeZone(entity.getSettlementTimeZone());
        response.setMcc(entity.getMcc());
        response.setStatementDescriptor(entity.getStatementDescriptor());
        response.setMetadataValueJson(maskMetadataJson(entity.getChannelId(), entity.getMetadataValueJson()));
        response.setMidStatus(entity.getMidStatus());
        response.setEffectiveTime(entity.getEffectiveTime());
        response.setExpireTime(entity.getExpireTime());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

/**
 * 构造midbinding响应对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
 * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
 * @param mid MID 输入值，参与 渠道 MID 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
 */
    private MerchantChannelMidBindingResponse toMidBindingResponse(MerchantChannelMidBindingDO entity,
                                                                   ChannelInfoDO channel,
                                                                   ChannelMidConfigDO mid) {
        MerchantChannelMidBindingResponse response = new MerchantChannelMidBindingResponse();
        response.setId(entity.getId());
        response.setMerchantId(entity.getMerchantId());
        response.setChannelId(entity.getChannelId());
        response.setChannelCode(entity.getChannelCode());
        response.setChannelName(channelName(channel));
        response.setMidConfigId(entity.getMidConfigId());
        response.setChannelMid(entity.getChannelMid());
        response.setMidName(mid == null ? null : mid.getMidName());
        response.setBindingStatus(entity.getBindingStatus());
        response.setEffectiveTime(entity.getEffectiveTime());
        response.setExpireTime(entity.getExpireTime());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    /**
     * 构造capability响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造limit响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param entity entity 输入值，参与 entity 的查询、校验、转换、写入或日志摘要
     * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造capabilityquery对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造limitquery对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造midquery对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
     */
    private LambdaQueryWrapper<ChannelMidConfigDO> buildMidQuery(ChannelMidConfigQuery query) {
        return Wrappers.<ChannelMidConfigDO>lambdaQuery()
                .eq(ChannelMidConfigDO::getDeleted, NOT_DELETED)
                .eq(query.getChannelId() != null, ChannelMidConfigDO::getChannelId, query.getChannelId())
                .eq(StringUtils.hasText(query.getChannelCode()), ChannelMidConfigDO::getChannelCode, normalizeCode(query.getChannelCode()))
                .like(StringUtils.hasText(query.getChannelMid()), ChannelMidConfigDO::getChannelMid, trim(query.getChannelMid()))
                .eq(StringUtils.hasText(query.getBusinessType()), ChannelMidConfigDO::getBusinessType, normalizeCode(query.getBusinessType()))
                .eq(query.getMidStatus() != null, ChannelMidConfigDO::getMidStatus, query.getMidStatus())
                .orderByDesc(ChannelMidConfigDO::getUpdateTime);
    }

    /**
     * 构造midbindingquery对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
     */
    private LambdaQueryWrapper<MerchantChannelMidBindingDO> buildMidBindingQuery(MerchantChannelMidBindingQuery query) {
        return Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(query.getMerchantId()), MerchantChannelMidBindingDO::getMerchantId, trim(query.getMerchantId()))
                .eq(query.getChannelId() != null, MerchantChannelMidBindingDO::getChannelId, query.getChannelId())
                .eq(StringUtils.hasText(query.getChannelCode()), MerchantChannelMidBindingDO::getChannelCode, normalizeCode(query.getChannelCode()))
                .eq(query.getMidConfigId() != null, MerchantChannelMidBindingDO::getMidConfigId, query.getMidConfigId())
                .eq(query.getBindingStatus() != null, MerchantChannelMidBindingDO::getBindingStatus, query.getBindingStatus())
                .orderByDesc(MerchantChannelMidBindingDO::getUpdateTime);
    }

    /**
     * 整理capabilityID按币种or品牌，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 更新capabilitycurrencies，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param capability capability 输入值，参与 渠道能力 的查询、校验、转换、写入或日志摘要
     * @param currencies currencies 输入值，参与 currencies 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 更新capabilitycardbrands，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param capability capability 输入值，参与 渠道能力 的查询、校验、转换、写入或日志摘要
     * @param cardBrands 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
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

    /**
     * 更新softdeletecapabilitychildren，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param capabilityId capability ID 输入值，参与 capabilityID 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 更新metadataschemas，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
     * @param schemas schemas 输入值，参与 schemas 的查询、校验、转换、写入或日志摘要
     */
    private void replaceMetadataSchemas(ChannelInfoDO channel, List<ChannelMetadataSchemaItem> schemas) {
        LocalDateTime now = LocalDateTime.now();
        String operatorName = currentOperatorName();
        metadataSchemaMapper.selectList(Wrappers.<ChannelMetadataSchemaDO>lambdaQuery()
                        .eq(ChannelMetadataSchemaDO::getDeleted, NOT_DELETED)
                        .eq(ChannelMetadataSchemaDO::getChannelId, channel.getId()))
                .forEach(row -> {
                    row.setDeleted(row.getId());
                    row.setUpdateBy(operatorName);
                    row.setUpdateTime(now);
                    metadataSchemaMapper.updateById(row);
                });
        if (schemas == null || schemas.isEmpty()) {
            return;
        }
        int index = 1;
        for (ChannelMetadataSchemaItem schema : schemas) {
            ChannelMetadataSchemaDO row = new ChannelMetadataSchemaDO();
            row.setChannelId(channel.getId());
            row.setChannelCode(channel.getChannelCode());
            row.setFieldKey(trim(schema.getFieldKey()));
            row.setFieldLabel(trim(schema.getFieldLabel()));
            row.setFieldType(normalizeCode(schema.getFieldType()));
            row.setRequiredFlag(defaultOne(schema.getRequiredFlag()));
            row.setSensitiveFlag(defaultZero(schema.getSensitiveFlag()));
            row.setValidationRegex(trimToNull(schema.getValidationRegex()));
            row.setPlaceholder(trimToNull(schema.getPlaceholder()));
            row.setDefaultValue(defaultZero(schema.getSensitiveFlag()) == ENABLED ? null : trimToNull(schema.getDefaultValue()));
            row.setSortOrder(schema.getSortOrder() == null ? index : schema.getSortOrder());
            row.setFieldStatus(defaultOne(schema.getFieldStatus()));
            row.setCreateBy(operatorName);
            row.setCreateTime(now);
            row.setUpdateBy(operatorName);
            row.setUpdateTime(now);
            row.setDeleted(NOT_DELETED);
            metadataSchemaMapper.insert(row);
            index++;
        }
    }

    /**
     * 查询metadataschemas，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<ChannelMetadataSchemaItem> listMetadataSchemas(Long channelId) {
        return metadataSchemaMapper.selectList(Wrappers.<ChannelMetadataSchemaDO>lambdaQuery()
                        .eq(ChannelMetadataSchemaDO::getDeleted, NOT_DELETED)
                        .eq(ChannelMetadataSchemaDO::getChannelId, channelId)
                        .orderByAsc(ChannelMetadataSchemaDO::getSortOrder)
                        .orderByAsc(ChannelMetadataSchemaDO::getId))
                .stream()
                .map(this::toMetadataSchemaItem)
                .toList();
    }

    /**
     * 构造metadataschemaitem对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
    private ChannelMetadataSchemaItem toMetadataSchemaItem(ChannelMetadataSchemaDO row) {
        ChannelMetadataSchemaItem item = new ChannelMetadataSchemaItem();
        item.setId(row.getId());
        item.setFieldKey(row.getFieldKey());
        item.setFieldLabel(row.getFieldLabel());
        item.setFieldType(row.getFieldType());
        item.setRequiredFlag(row.getRequiredFlag());
        item.setSensitiveFlag(row.getSensitiveFlag());
        item.setValidationRegex(row.getValidationRegex());
        item.setPlaceholder(row.getPlaceholder());
        item.setDefaultValue(row.getDefaultValue());
        item.setSortOrder(row.getSortOrder());
        item.setFieldStatus(row.getFieldStatus());
        return item;
    }

    /**
     * 查询capabilitycurrencies，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param capabilityId capability ID 输入值，参与 capabilityID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<String> listCapabilityCurrencies(Long capabilityId) {
        return capabilityCurrencyMapper.selectList(Wrappers.<ChannelCapabilityCurrencyDO>lambdaQuery()
                        .eq(ChannelCapabilityCurrencyDO::getDeleted, NOT_DELETED)
                        .eq(ChannelCapabilityCurrencyDO::getCapabilityId, capabilityId)
                        .orderByAsc(ChannelCapabilityCurrencyDO::getCurrencyCode))
                .stream().map(ChannelCapabilityCurrencyDO::getCurrencyCode).toList();
    }

    /**
     * 查询capabilitycardbrands，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param capabilityId capability ID 输入值，参与 capabilityID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<String> listCapabilityCardBrands(Long capabilityId) {
        return capabilityCardBrandMapper.selectList(Wrappers.<ChannelCapabilityCardBrandDO>lambdaQuery()
                        .eq(ChannelCapabilityCardBrandDO::getDeleted, NOT_DELETED)
                        .eq(ChannelCapabilityCardBrandDO::getCapabilityId, capabilityId)
                        .eq(ChannelCapabilityCardBrandDO::getBrandStatus, ENABLED)
                        .orderByAsc(ChannelCapabilityCardBrandDO::getSortOrder))
                .stream().map(ChannelCapabilityCardBrandDO::getCardBrand).toList();
    }

    /**
     * 规范化capabilitymethods，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 整理渠道map，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param channelIds channel Ids 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<Long, ChannelInfoDO> channelMap(List<Long> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return Map.of();
        }
        return channelInfoMapper.selectBatchIds(channelIds).stream()
                .collect(Collectors.toMap(ChannelInfoDO::getId, row -> row, (left, right) -> left));
    }

    /**
     * 规范化midmap，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param midIds MID Ids 输入值，参与 midID 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<Long, ChannelMidConfigDO> midMap(List<Long> midIds) {
        if (midIds == null || midIds.isEmpty()) {
            return Map.of();
        }
        return midConfigMapper.selectBatchIds(midIds).stream()
                .collect(Collectors.toMap(ChannelMidConfigDO::getId, row -> row, (left, right) -> left));
    }

    /**
     * 查询渠道，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private ChannelInfoDO findChannel(Long id) {
        ChannelInfoDO entity = channelInfoMapper.selectOne(Wrappers.<ChannelInfoDO>lambdaQuery()
                .eq(ChannelInfoDO::getDeleted, NOT_DELETED)
                .eq(ChannelInfoDO::getId, id));
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "渠道不存在");
        }
        return entity;
    }

    /**
     * 查询渠道能力，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private ChannelPaymentCapabilityDO findCapability(Long id) {
        ChannelPaymentCapabilityDO entity = capabilityMapper.selectOne(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getId, id));
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "渠道支付能力不存在");
        }
        return entity;
    }

    /**
     * 查询限额，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private ChannelLimitRuleDO findLimit(Long id) {
        ChannelLimitRuleDO entity = limitRuleMapper.selectOne(Wrappers.<ChannelLimitRuleDO>lambdaQuery()
                .eq(ChannelLimitRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelLimitRuleDO::getId, id));
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "渠道限额不存在");
        }
        return entity;
    }

    /**
     * 查询渠道 MID，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private ChannelMidConfigDO findMid(Long id) {
        ChannelMidConfigDO entity = midConfigMapper.selectOne(Wrappers.<ChannelMidConfigDO>lambdaQuery()
                .eq(ChannelMidConfigDO::getDeleted, NOT_DELETED)
                .eq(ChannelMidConfigDO::getId, id));
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "渠道MID配置不存在");
        }
        return entity;
    }

    /**
     * 查询渠道 MID 绑定，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private MerchantChannelMidBindingDO findMidBinding(Long id) {
        MerchantChannelMidBindingDO entity = midBindingMapper.selectOne(Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED)
                .eq(MerchantChannelMidBindingDO::getId, id));
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户渠道MID绑定不存在");
        }
        return entity;
    }

    /**
     * 判断 has active capability 条件是否成立，用于控制 Admin Channel Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasActiveCapability(Long channelId) {
        return capabilityMapper.selectCount(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, channelId)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED)) > 0;
    }

    /**
     * 判断 has enabled capability 条件是否成立，用于控制 Admin Channel Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param paymentMethod payment Method 输入值，参与 paymentmethod 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasEnabledCapability(Long channelId, String businessType, String paymentMethod) {
        return capabilityMapper.selectCount(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, channelId)
                .eq(ChannelPaymentCapabilityDO::getBusinessType, businessType)
                .eq(StringUtils.hasText(paymentMethod), ChannelPaymentCapabilityDO::getPaymentMethod, paymentMethod)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED)) > 0;
    }

    /**
     * 查询enabledcapability，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param paymentMethod payment Method 输入值，参与 paymentmethod 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private ChannelPaymentCapabilityDO findEnabledCapability(Long channelId, String businessType, String paymentMethod) {
        return capabilityMapper.selectOne(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, channelId)
                .eq(ChannelPaymentCapabilityDO::getBusinessType, businessType)
                .eq(ChannelPaymentCapabilityDO::getPaymentMethod, paymentMethod)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED));
    }

    /**
     * 判断 has active limit 条件是否成立，用于控制 Admin Channel Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasActiveLimit(Long channelId) {
        return limitRuleMapper.selectCount(Wrappers.<ChannelLimitRuleDO>lambdaQuery()
                .eq(ChannelLimitRuleDO::getDeleted, NOT_DELETED)
                .eq(ChannelLimitRuleDO::getChannelId, channelId)
                .eq(ChannelLimitRuleDO::getRuleStatus, ENABLED)) > 0;
    }

    /**
     * 判断 has active mid binding 条件是否成立，用于控制 Admin Channel Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param midConfigId MID Config ID 输入值，参与 mid配置ID 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasActiveMidBinding(Long midConfigId) {
        return midBindingMapper.selectCount(Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED)
                .eq(MerchantChannelMidBindingDO::getMidConfigId, midConfigId)
                .eq(MerchantChannelMidBindingDO::getBindingStatus, ENABLED)) > 0;
    }

    /**
     * 整理refreshbinding渠道mid，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param mid MID 输入值，参与 渠道 MID 的查询、校验、转换、写入或日志摘要
     */
    private void refreshBindingChannelMid(ChannelMidConfigDO mid) {
        List<MerchantChannelMidBindingDO> bindings = midBindingMapper.selectList(Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED)
                .eq(MerchantChannelMidBindingDO::getMidConfigId, mid.getId()));
        LocalDateTime now = LocalDateTime.now();
        for (MerchantChannelMidBindingDO binding : bindings) {
            binding.setChannelId(mid.getChannelId());
            binding.setChannelCode(mid.getChannelCode());
            binding.setChannelMid(mid.getChannelMid());
            binding.setUpdateBy(currentOperatorName());
            binding.setUpdateTime(now);
            midBindingMapper.updateById(binding);
        }
    }

    /**
     * 校验渠道supportsbusiness输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 整理渠道supports3ds，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private boolean channelSupports3ds(ChannelInfoDO channel, String businessType) {
        return BUSINESS_ACQUIRING.equals(businessType)
                && defaultZero(channel.getSupportAcquiring()) == ENABLED
                && defaultZero(channel.getSupport3ds()) == ENABLED;
    }

    /**
     * 校验paymentmethod输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param paymentMethod payment Method 输入值，参与 paymentmethod 的查询、校验、转换、写入或日志摘要
     */
    private void validatePaymentMethod(String businessType, String paymentMethod) {
        if (BUSINESS_ACQUIRING.equals(businessType)) {
            assertDictValue("acquiring_payment_method", paymentMethod, true);
        } else {
            assertDictValue("payout_payment_method", paymentMethod, true);
        }
    }

    /**
     * 查询enabledcapabilities，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<ChannelPaymentCapabilityDO> listEnabledCapabilities(Long channelId, String businessType) {
        return capabilityMapper.selectList(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .eq(ChannelPaymentCapabilityDO::getChannelId, channelId)
                .eq(ChannelPaymentCapabilityDO::getBusinessType, businessType)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED)
                .orderByAsc(ChannelPaymentCapabilityDO::getSortOrder)
                .orderByAsc(ChannelPaymentCapabilityDO::getId));
    }

    /**
     * 解析resolvemid交易typescope，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param paymentMethodScope payment Method Scope 输入值，参与 paymentmethodscope 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveMidTransactionTypeScope(Long channelId, String businessType, String paymentMethodScope) {
        if (BUSINESS_PAYOUT.equals(businessType)) {
            return NONE;
        }
        List<ChannelPaymentCapabilityDO> capabilities = filterMidCapabilities(channelId, businessType, paymentMethodScope);
        List<String> transactionTypes = capabilities.stream()
                .flatMap(capability -> splitTransactionTypes(businessType, capability.getTransactionType()).stream())
                .distinct()
                .toList();
        if (transactionTypes.isEmpty()) {
            throw badRequest("所选支付方式未配置启用的交易类型能力");
        }
        return joinTransactionTypes(transactionTypes);
    }

    /**
     * 解析resolvemidcard品牌scope，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param paymentMethodScope payment Method Scope 输入值，参与 paymentmethodscope 的查询、校验、转换、写入或日志摘要
     * @param cardBrandScope 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 构造、转换或解析后的业务值
     */
    private String resolveMidCardBrandScope(Long channelId, String businessType, String paymentMethodScope, String cardBrandScope) {
        boolean includesBankCard = ALL.equals(paymentMethodScope)
                || splitScope(paymentMethodScope).contains(PAYMENT_BANK_CARD);
        if (!includesBankCard) {
            return NONE;
        }
        ChannelPaymentCapabilityDO bankCardCapability = findEnabledCapability(channelId, businessType, PAYMENT_BANK_CARD);
        if (bankCardCapability == null) {
            throw badRequest("银行卡支付方式不存在启用中的支付能力");
        }
        String normalizedScope = normalizeScope(cardBrandScope, "卡品牌范围");
        if (ALL.equals(normalizedScope)) {
            return ALL;
        }
        for (String cardBrand : splitScope(normalizedScope)) {
            assertDictValue("card_brand", cardBrand, true);
            Long count = capabilityCardBrandMapper.selectCount(Wrappers.<ChannelCapabilityCardBrandDO>lambdaQuery()
                    .eq(ChannelCapabilityCardBrandDO::getDeleted, NOT_DELETED)
                    .eq(ChannelCapabilityCardBrandDO::getCapabilityId, bankCardCapability.getId())
                    .eq(ChannelCapabilityCardBrandDO::getCardBrand, cardBrand)
                    .eq(ChannelCapabilityCardBrandDO::getBrandStatus, ENABLED));
            if (count <= 0) {
                throw badRequest("该银行卡支付能力未绑定启用的卡品牌：" + cardBrand);
            }
        }
        return normalizedScope;
    }

    /**
     * 整理筛选midcapabilities，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param paymentMethodScope payment Method Scope 输入值，参与 paymentmethodscope 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private List<ChannelPaymentCapabilityDO> filterMidCapabilities(Long channelId, String businessType, String paymentMethodScope) {
        List<ChannelPaymentCapabilityDO> capabilities = listEnabledCapabilities(channelId, businessType);
        if (capabilities.isEmpty()) {
            throw badRequest("该渠道业务类型下不存在启用中的支付能力");
        }
        if (ALL.equals(paymentMethodScope)) {
            return capabilities;
        }
        Map<String, ChannelPaymentCapabilityDO> capabilityMap = capabilities.stream()
                .collect(Collectors.toMap(ChannelPaymentCapabilityDO::getPaymentMethod, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<ChannelPaymentCapabilityDO> selected = new ArrayList<>();
        for (String paymentMethod : splitScope(paymentMethodScope)) {
            validatePaymentMethod(businessType, paymentMethod);
            ChannelPaymentCapabilityDO capability = capabilityMap.get(paymentMethod);
            if (capability == null) {
                throw badRequest("该渠道支付方式不存在启用中的支付能力：" + paymentMethod);
            }
            selected.add(capability);
        }
        return selected;
    }

    /**
     * 校验断言dictvalue输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param dictType dict Type 输入值，参与 dicttype 的查询、校验、转换、写入或日志摘要
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param required required 输入值，参与 required 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 校验businesstype输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     */
    private void validateBusinessType(String businessType) {
        if (!BUSINESS_ACQUIRING.equals(businessType) && !BUSINESS_PAYOUT.equals(businessType)) {
            throw badRequest("业务类型必须为 ACQUIRING 或 PAYOUT");
        }
    }

    /**
     * 校验状态输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     */
    private void validateStatus(Integer status) {
        if (status == null || (status != ENABLED && status != DISABLED)) {
            throw badRequest("状态必须为0停用或1启用");
        }
    }

    /**
     * 校验timeoutseconds输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param seconds seconds 输入值，参与 seconds 的查询、校验、转换、写入或日志摘要
     * @param fieldName field Name 输入值，参与 fieldname 的查询、校验、转换、写入或日志摘要
     */
    private void validateTimeoutSeconds(Integer seconds, String fieldName) {
        if (seconds != null && (seconds <= 0 || seconds > 300)) {
            throw badRequest(fieldName + "必须在1到300秒之间");
        }
    }

    /**
     * 整理默认timeout，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param seconds seconds 输入值，参与 seconds 的查询、校验、转换、写入或日志摘要
     * @param defaultValue default Value 输入值，参与 默认value 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int defaultTimeout(Integer seconds, int defaultValue) {
        return seconds == null ? defaultValue : seconds;
    }

    /**
     * 解析normalizescope，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param fieldName field Name 输入值，参与 fieldname 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeScope(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw badRequest(fieldName + "不能为空");
        }
        if (ALL.equalsIgnoreCase(trim(value))) {
            return ALL;
        }
        return String.join(TRANSACTION_TYPE_SEPARATOR, normalizeCodes(List.of(value.split(TRANSACTION_TYPE_SEPARATOR))));
    }

    /**
     * 校验paymentmethodscope输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param channelId channel ID 输入值，参与 渠道ID 的查询、校验、转换、写入或日志摘要
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param scope scope 输入值，参与 scope 的查询、校验、转换、写入或日志摘要
     */
    private void validatePaymentMethodScope(Long channelId, String businessType, String scope) {
        filterMidCapabilities(channelId, businessType, scope);
    }

    /**
     * 校验币种scope输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param scope scope 输入值，参与 scope 的查询、校验、转换、写入或日志摘要
     */
    private void validateCurrencyScope(String scope) {
        if (ALL.equals(scope)) {
            return;
        }
        for (String value : splitScope(scope)) {
            assertDictValue("iso_currency", value, true);
        }
    }

    /**
     * 解析normalizesettlementcycle，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeSettlementCycle(String value) {
        if (!StringUtils.hasText(value)) {
            throw badRequest("结算周期不能为空");
        }
        String normalized = normalizeCode(value).replace(" ", "");
        if (normalized.matches("^T\\+?\\d{1,3}$")) {
            return "T+" + normalized.substring(normalized.indexOf('T') + 1).replace("+", "");
        }
        throw badRequest("结算周期必须为 T+N 格式");
    }

    /**
     * 规范化splitscope，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param scope scope 输入值，参与 scope 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private List<String> splitScope(String scope) {
        if (!StringUtils.hasText(scope) || ALL.equals(scope)) {
            return List.of();
        }
        return normalizeCodes(List.of(scope.split(TRANSACTION_TYPE_SEPARATOR)));
    }

    /**
     * 按渠道元数据 Schema 校验 MID 元数据的必填项和正则格式。
     *
     * @param channelId 渠道主键
     * @param metadataValueJson MID 元数据 JSON，可能包含渠道密钥等敏感值
     * @throws com.scott.payment.component.core.exception.ServiceException 必填值缺失或格式不合法时抛出
     */
    @SuppressWarnings("unchecked")
    private void validateMetadataValues(Long channelId, String metadataValueJson) {
        Map<String, Object> values = StringUtils.hasText(metadataValueJson)
                ? JsonUtils.parseObject(metadataValueJson, Map.class)
                : Map.of();
        if (values == null) {
            values = Map.of();
        }
        for (ChannelMetadataSchemaItem schema : listMetadataSchemas(channelId)) {
            if (schema.getFieldStatus() != ENABLED) {
                continue;
            }
            Object value = values.get(schema.getFieldKey());
            if (schema.getRequiredFlag() == ENABLED && (value == null || !StringUtils.hasText(String.valueOf(value)))) {
                throw badRequest("MID元数据必填：" + schema.getFieldLabel());
            }
            if (StringUtils.hasText(schema.getValidationRegex()) && value != null && StringUtils.hasText(String.valueOf(value))
                    && !Pattern.compile(schema.getValidationRegex()).matcher(String.valueOf(value)).matches()) {
                throw badRequest("MID元数据格式不合法：" + schema.getFieldLabel());
            }
        }
    }

    /**
     * 从常见渠道商户号元数据字段解析 MID，未提供时回退到显式 channelMid。
     *
     * @param request MID 保存请求
     * @return 去除首尾空白后的渠道 MID
     */
    private String resolveChannelMid(ChannelMidConfigSaveRequest request) {
        String value = firstTextMetadataValue(request.getMetadataValueJson(),
                "merchantId", "merchant_id", "channelMid", "channel_mid", "mid", "midNo", "mid_no", "merchantNo", "merchant_no");
        if (StringUtils.hasText(value)) {
            return trim(value);
        }
        if (StringUtils.hasText(request.getChannelMid())) {
            return trim(request.getChannelMid());
        }
        throw badRequest("MID元数据中缺少渠道MID或商户号");
    }

    /**
     * 整理首个文本metadatavalue，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param metadataValueJson metadata Value Json 输入值，参与 metadatavaluejson 的查询、校验、转换、写入或日志摘要
     * @param keys 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String firstTextMetadataValue(String metadataValueJson, String... keys) {
        Map<String, Object> values = parseMetadataMap(metadataValueJson);
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    /**
     * 合并 MID 更新中的敏感元数据，掩码或空值表示保留既有秘密值。
     *
     * <p>仅 Schema 标记为敏感的字段允许保留旧值，普通字段仍以本次请求为准，
     * 避免管理端编辑脱敏响应时意外覆盖渠道密钥。</p>
     *
     * @param entity 已持久化的 MID 配置
     * @param request 本次更新请求
     */
    @SuppressWarnings("unchecked")
    private void mergeMetadataValuesForUpdate(ChannelMidConfigDO entity, ChannelMidConfigSaveRequest request) {
        Map<String, Object> incoming = parseMetadataMap(request.getMetadataValueJson());
        if (incoming.isEmpty() || !StringUtils.hasText(entity.getMetadataValueJson())) {
            return;
        }
        Map<String, Object> existing = parseMetadataMap(entity.getMetadataValueJson());
        if (existing.isEmpty()) {
            return;
        }
        Map<String, Object> merged = new LinkedHashMap<>(incoming);
        for (ChannelMetadataSchemaItem schema : listMetadataSchemas(entity.getChannelId())) {
            if (!shouldMaskMetadata(schema) || !existing.containsKey(schema.getFieldKey())) {
                continue;
            }
            Object submittedValue = incoming.get(schema.getFieldKey());
            if (submittedValue == null || !StringUtils.hasText(String.valueOf(submittedValue))
                    || MASKED_METADATA_VALUE.equals(String.valueOf(submittedValue).trim())) {
                merged.put(schema.getFieldKey(), existing.get(schema.getFieldKey()));
            }
        }
        request.setMetadataValueJson(JsonUtils.toJsonString(merged));
    }

    /**
     * 将 MID 元数据 JSON 解析为键值映射。
     *
     * @param metadataValueJson 元数据 JSON
     * @return 元数据映射；输入为空或 JSON 结果为空时返回空映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadataMap(String metadataValueJson) {
        if (!StringUtils.hasText(metadataValueJson)) {
            return Map.of();
        }
        Map<String, Object> values = JsonUtils.parseObject(metadataValueJson, Map.class);
        return values == null ? Map.of() : values;
    }

    /**
     * 按渠道 Schema 将敏感 MID 元数据替换为固定掩码后再返回管理端。
     *
     * @param channelId 渠道主键
     * @param metadataValueJson 持久化元数据 JSON，可能包含渠道秘密值
     * @return 敏感字段已替换为掩码的 JSON
     */
    private String maskMetadataJson(Long channelId, String metadataValueJson) {
        if (!StringUtils.hasText(metadataValueJson)) {
            return metadataValueJson;
        }
        Map<String, Object> values = parseMetadataMap(metadataValueJson);
        if (values.isEmpty()) {
            return metadataValueJson;
        }
        Map<String, Object> masked = new LinkedHashMap<>(values);
        for (ChannelMetadataSchemaItem schema : listMetadataSchemas(channelId)) {
            if (!masked.containsKey(schema.getFieldKey()) || !shouldMaskMetadata(schema)) {
                continue;
            }
            masked.put(schema.getFieldKey(), MASKED_METADATA_VALUE);
        }
        return JsonUtils.toJsonString(masked);
    }

    /**
     * 整理should脱敏metadata，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param schema schema 输入值，参与 schema 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private boolean shouldMaskMetadata(ChannelMetadataSchemaItem schema) {
        return defaultZero(schema.getSensitiveFlag()) == ENABLED || isSensitiveMetadataType(schema.getFieldType());
    }

    /**
     * 判断 is sensitive metadata type 条件是否成立，用于控制 Admin Channel Service Impl 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param fieldType field Type 输入值，参与 fieldtype 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isSensitiveMetadataType(String fieldType) {
        return Set.of("PASSWORD", "PRIVATE_KEY").contains(normalizeCode(fieldType));
    }

    /**
     * 解析normalize交易type，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param transactionType transaction Type 输入值，参与 交易type 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeTransactionType(String businessType, String transactionType) {
        if (BUSINESS_PAYOUT.equals(businessType)) {
            return NONE;
        }
        return normalizeCode(transactionType);
    }

    /**
     * 解析normalize交易types，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param transactionTypes transaction Types 输入值，参与 交易types 的查询、校验、转换、写入或日志摘要
     * @param transactionType transaction Type 输入值，参与 交易type 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 整理拼接交易types，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param transactionTypes transaction Types 输入值，参与 交易types 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String joinTransactionTypes(List<String> transactionTypes) {
        return String.join(TRANSACTION_TYPE_SEPARATOR, transactionTypes);
    }

    /**
     * 整理split交易types，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param businessType business Type 输入值，参与 businesstype 的查询、校验、转换、写入或日志摘要
     * @param transactionType transaction Type 输入值，参与 交易type 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private List<String> splitTransactionTypes(String businessType, String transactionType) {
        if (BUSINESS_PAYOUT.equals(businessType)) {
            return List.of(NONE);
        }
        if (!StringUtils.hasText(transactionType)) {
            return List.of();
        }
        return normalizeCodes(List.of(transactionType.split(TRANSACTION_TYPE_SEPARATOR)));
    }

    /**
     * 整理默认scope，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String defaultScope(String value) {
        return StringUtils.hasText(value) ? normalizeCode(value) : ALL;
    }

    /**
     * 解析normalize编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeCode(String value) {
        return trim(value).toUpperCase();
    }

    /**
     * 解析normalizecodes，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param values values 输入值，参与 值 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 整理默认zero，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 整理默认one，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int defaultOne(Integer value) {
        return value == null ? 1 : value;
    }

    /**
     * 规范化trim，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 规范化trimtonull，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 整理渠道name，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param channel channel 输入值，参与 渠道 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String channelName(ChannelInfoDO channel) {
        if (channel == null) {
            return "";
        }
        return StringUtils.hasText(channel.getChannelCnName()) ? channel.getChannelCnName() : channel.getChannelEnName();
    }

    /**
     * 在渠道配置写入前登记指定商户的路由永久快照失效意图。
     *
     * @param merchantId 受渠道配置变更影响的商户号
     */
    private void prepareRouteInvalidation(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return;
        }
        cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_ROUTE, merchantId.trim());
    }

    /**
     * 查找绑定到指定 MID 的商户并逐一登记路由快照失效。
     *
     * @param midConfigId 发生变更的 MID 配置主键
     */
    private void prepareRouteInvalidationByMid(Long midConfigId) {
        if (midConfigId == null) {
            return;
        }
        midBindingMapper.selectList(Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                        .eq(MerchantChannelMidBindingDO::getMidConfigId, midConfigId)
                        .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED))
                .stream()
                .map(MerchantChannelMidBindingDO::getMerchantId)
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(this::prepareRouteInvalidation);
    }

    /**
     * 查找指定渠道下全部 MID 绑定商户并逐一登记路由快照失效。
     *
     * <p>渠道启停、请求地址、能力和币种范围都会改变交易选路结果，必须在相应数据库写入前执行。</p>
     *
     * @param channelId 发生变更的渠道主键
     */
    private void prepareRouteInvalidationByChannel(Long channelId) {
        if (channelId == null) {
            return;
        }
        Set<Long> midIds = midConfigMapper.selectList(Wrappers.<ChannelMidConfigDO>lambdaQuery()
                        .eq(ChannelMidConfigDO::getChannelId, channelId)
                        .eq(ChannelMidConfigDO::getDeleted, NOT_DELETED))
                .stream()
                .map(ChannelMidConfigDO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (midIds.isEmpty()) {
            return;
        }
        midBindingMapper.selectList(Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                        .in(MerchantChannelMidBindingDO::getMidConfigId, midIds)
                        .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED))
                .stream()
                .map(MerchantChannelMidBindingDO::getMerchantId)
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(this::prepareRouteInvalidation);
    }

    /**
     * 整理当前操作人名称，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 整理bad请求，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
