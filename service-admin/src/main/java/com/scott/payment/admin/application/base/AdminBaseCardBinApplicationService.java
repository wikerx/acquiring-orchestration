package com.scott.payment.admin.application.base;

import com.scott.payment.admin.application.base.cache.CardBinCacheInvalidationCoordinator;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.base.CardBinDTOs;
import com.scott.payment.admin.dto.export.CardBinExportRow;
import com.scott.payment.admin.entity.base.CardBinEntities;
import com.scott.payment.admin.mapper.BaseCardBinImportBatchMapper;
import com.scott.payment.admin.mapper.BaseCardBinRangeMapper;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseCardBinApplicationService
 * @date : 2026-07-04 00:00
 * @email : scott_x@163.com
 * @description : 卡 BIN 管理应用服务，位于 service-admin 应用编排层，负责 BIN 区间维护、旧库初始化、字典聚合和匹配测试。
 * @status : create
 */
@Service
public class AdminBaseCardBinApplicationService {

    /**
     * EXPORT TIME FORMATTER，用于保存 Admin Base Card Bin Application Service 中与 exporttimeformatter 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /**
     * BATCH TIME FORMATTER，用于保存 Admin Base Card Bin Application Service 中与 batchtimeformatter 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final DateTimeFormatter BATCH_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /**
     * NOT DELETED，用于保存 Admin Base Card Bin Application Service 中与 notdeleted 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long NOT_DELETED = AuthConstants.NOT_DELETED;
    /**
     * STATUS DISABLED，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final int STATUS_DISABLED = 0;
    /**
     * STATUS ENABLED，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final int STATUS_ENABLED = 1;
    /**
     * STATUS PENDING，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final int STATUS_PENDING = 2;
    /**
     * STATUS EXPIRED，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final int STATUS_EXPIRED = 3;
    /**
     * DEFAULT PRIORITY，用于保存 Admin Base Card Bin Application Service 中与 defaultpriority 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int DEFAULT_PRIORITY = 50;
    /**
     * NORMALIZED BIN LENGTH，用于保存 Admin Base Card Bin Application Service 中与 normalizedbinlength 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int NORMALIZED_BIN_LENGTH = 11;
    /**
     * CARD BRAND DICT，用于保存 Admin Base Card Bin Application Service 中与 cardbranddict 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String CARD_BRAND_DICT = "card_brand";
    /**
     * CARD TYPE DICT，用于区分 Admin Base Card Bin Application Service 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String CARD_TYPE_DICT = "base_card_type";
    /**
     * CARD BIN STATUS DICT，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String CARD_BIN_STATUS_DICT = "base_card_bin_status";
    /**
     * DATA SOURCE DICT，用于保存 Admin Base Card Bin Application Service 中与 data来源dict 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DATA_SOURCE_DICT = "base_card_bin_data_source";
    /**
     * DEFAULT LOCALE，用于保存 Admin Base Card Bin Application Service 中与 defaultlocale 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_LOCALE = "zh-CN";
    /**
     * DATA SOURCE MANUAL，用于保存 Admin Base Card Bin Application Service 中与 data来源manual 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DATA_SOURCE_MANUAL = "MANUAL";
    /**
     * DATA SOURCE LEGACY，用于保存 Admin Base Card Bin Application Service 中与 data来源legacy 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DATA_SOURCE_LEGACY = "LEGACY_DB";
    /**
     * IMPORT TYPE DB INIT，用于区分 Admin Base Card Bin Application Service 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String IMPORT_TYPE_DB_INIT = "DB_INIT";
    private static final Set<Integer> VALID_STATUSES = Set.of(STATUS_DISABLED, STATUS_ENABLED, STATUS_PENDING, STATUS_EXPIRED);

    /**
     * 卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。
     * <p>
     * 单位：无；格式：卡 BIN 或尾号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：仅保存识别片段，不保存完整 PAN；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final BaseCardBinRangeMapper cardBinRangeMapper;
    /**
     * import Batch Mapper 依赖，用于 Admin Base Card Bin Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final BaseCardBinImportBatchMapper importBatchMapper;
    /**
     * admin Dict Service 依赖，用于 Admin Base Card Bin Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminDictService adminDictService;
    /**
     * ISO Country Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持国家地区；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final IsoCountryMapper isoCountryMapper;
    /**
     * excel Export Service 依赖，用于 Admin Base Card Bin Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelExportService excelExportService;
    /**
     * excel I 18 n Message Resolver，用于保存 Admin Base Card Bin Application Service 中与 exceli18nmessageresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    /**
     * excel Locale Resolver，用于保存 Admin Base Card Bin Application Service 中与 excellocaleresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelLocaleResolver excelLocaleResolver;

    /** Card BIN 数据变更与 Redis generation、可靠 MQ 的事务协调器。 */
    private final CardBinCacheInvalidationCoordinator cacheInvalidationCoordinator;

    /**
     * 创建卡 BIN 管理应用服务。
     *
     * @param cardBinRangeMapper 卡 BIN 区间 Mapper
     * @param importBatchMapper 导入批次 Mapper
     * @param adminDictService 字典服务
     * @param isoCountryMapper 国家地区 Mapper
     * @param excelExportService Excel 导出服务
     * @param excelI18nMessageResolver Excel 国际化解析器
     * @param excelLocaleResolver Excel 语言解析器
     */
    public AdminBaseCardBinApplicationService(BaseCardBinRangeMapper cardBinRangeMapper,
                                              BaseCardBinImportBatchMapper importBatchMapper,
                                              AdminDictService adminDictService,
                                              IsoCountryMapper isoCountryMapper,
                                              ExcelExportService excelExportService,
                                              ExcelI18nMessageResolver excelI18nMessageResolver,
                                              ExcelLocaleResolver excelLocaleResolver,
                                              CardBinCacheInvalidationCoordinator cacheInvalidationCoordinator) {
        this.cardBinRangeMapper = cardBinRangeMapper;
        this.importBatchMapper = importBatchMapper;
        this.adminDictService = adminDictService;
        this.isoCountryMapper = isoCountryMapper;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
    }

    /**
     * 分页查询卡 BIN 区间。
     *
     * @param request 查询请求
     * @return 卡 BIN 分页数据
     */
    @DS(DataSourceName.SLAVE)
    public PageResult<CardBinDTOs.CardBinResponse> page(CardBinDTOs.CardBinQueryRequest request) {
        CardBinDTOs.CardBinQueryRequest query = request == null ? new CardBinDTOs.CardBinQueryRequest() : request;
        LambdaQueryWrapper<CardBinEntities.BaseCardBinRangeDO> wrapper = buildListQuery(query);
        Page<CardBinEntities.BaseCardBinRangeDO> page = cardBinRangeMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                wrapper
        );
        DictLabels labels = loadDictLabels();
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream().map(row -> toResponse(row, labels)).toList()
        );
    }

    /**
     * 查询卡 BIN 详情。
     *
     * @param id 主键 ID
     * @return 卡 BIN 详情
     */
    @DS(DataSourceName.SLAVE)
    public CardBinDTOs.CardBinResponse detail(Long id) {
        return toResponse(getActiveRow(id), loadDictLabels());
    }

    /**
     * 新增卡 BIN 区间。
     *
     * @param request 保存请求
     * @return 保存后的卡 BIN 数据
     */
    @Transactional(rollbackFor = Exception.class)
    @DS(DataSourceName.MASTER)
    public CardBinDTOs.CardBinResponse create(CardBinDTOs.CardBinSaveRequest request) {
        NormalizedBinRange range = normalizeRange(request.getCardBinStart(), request.getCardBinEnd());
        assertDictValue(CARD_BRAND_DICT, request.getCardBrand(), "卡品牌不存在或已停用");
        assertDictValue(CARD_TYPE_DICT, request.getCardType(), "卡类型不存在或已停用");
        String dataSource = defaultIfBlank(request.getDataSource(), DATA_SOURCE_MANUAL);
        assertDictValue(DATA_SOURCE_DICT, dataSource, "数据来源不存在或已停用");
        Integer status = normalizeStatus(request.getStatus(), STATUS_ENABLED);
        status = adjustStatusByConflicts(null, range, status);

        LocalDateTime now = LocalDateTime.now();
        CardBinEntities.BaseCardBinRangeDO row = new CardBinEntities.BaseCardBinRangeDO();
        fillSaveFields(row, request, range, dataSource, status);
        row.setCreateBy(currentOperatorName());
        row.setUpdateBy(currentOperatorName());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setDeleted(NOT_DELETED);
        cacheInvalidationCoordinator.prepare();
        cardBinRangeMapper.insert(row);
        return toResponse(row, loadDictLabels());
    }

    /**
     * 更新卡 BIN 区间。
     *
     * @param id 主键 ID
     * @param request 保存请求
     * @return 更新后的卡 BIN 数据
     */
    @Transactional(rollbackFor = Exception.class)
    @DS(DataSourceName.MASTER)
    public CardBinDTOs.CardBinResponse update(Long id, CardBinDTOs.CardBinSaveRequest request) {
        CardBinEntities.BaseCardBinRangeDO row = getActiveRow(id);
        NormalizedBinRange range = normalizeRange(request.getCardBinStart(), request.getCardBinEnd());
        assertDictValue(CARD_BRAND_DICT, request.getCardBrand(), "卡品牌不存在或已停用");
        assertDictValue(CARD_TYPE_DICT, request.getCardType(), "卡类型不存在或已停用");
        String dataSource = defaultIfBlank(request.getDataSource(), row.getDataSource());
        assertDictValue(DATA_SOURCE_DICT, dataSource, "数据来源不存在或已停用");
        Integer status = normalizeStatus(request.getStatus(), row.getStatus());
        status = adjustStatusByConflicts(row.getId(), range, status);

        fillSaveFields(row, request, range, dataSource, status);
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
        cacheInvalidationCoordinator.prepare();
        cardBinRangeMapper.updateById(row);
        return toResponse(row, loadDictLabels());
    }

    /**
     * 逻辑删除卡 BIN 区间。
     *
     * @param id 主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    @DS(DataSourceName.MASTER)
    public void remove(Long id) {
        CardBinEntities.BaseCardBinRangeDO row = getActiveRow(id);
        row.setStatus(STATUS_DISABLED);
        row.setDeleted(row.getId());
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
        cacheInvalidationCoordinator.prepare();
        cardBinRangeMapper.updateById(row);
    }

    /**
     * 更新卡 BIN 状态。
     *
     * @param id 主键 ID
     * @param request 状态请求
     * @return 更新后的卡 BIN 数据
     */
    @Transactional(rollbackFor = Exception.class)
    @DS(DataSourceName.MASTER)
    public CardBinDTOs.CardBinResponse updateStatus(Long id, CardBinDTOs.CardBinStatusRequest request) {
        CardBinEntities.BaseCardBinRangeDO row = getActiveRow(id);
        Integer targetStatus = normalizeStatus(request.getStatus(), null);
        assertStatusTransition(row.getStatus(), targetStatus);
        row.setStatus(targetStatus);
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
        cacheInvalidationCoordinator.prepare();
        cardBinRangeMapper.updateById(row);
        return toResponse(row, loadDictLabels());
    }

    /**
     * 执行卡 BIN 匹配测试。
     *
     * @param request 匹配测试请求
     * @return 匹配结果
     */
    public CardBinDTOs.CardBinMatchResponse match(CardBinDTOs.CardBinMatchRequest request) {
        NormalizedBinRange range = normalizeRange(request.getCardBin(), null);
        long value = range.start();
        List<CardBinEntities.BaseCardBinRangeDO> rows = cardBinRangeMapper.selectList(
                Wrappers.<CardBinEntities.BaseCardBinRangeDO>lambdaQuery()
                        .eq(CardBinEntities.BaseCardBinRangeDO::getDeleted, NOT_DELETED)
                        .eq(CardBinEntities.BaseCardBinRangeDO::getStatus, STATUS_ENABLED)
                        .le(CardBinEntities.BaseCardBinRangeDO::getCardBinStart, value)
                        .ge(CardBinEntities.BaseCardBinRangeDO::getCardBinEnd, value)
                        .orderByDesc(CardBinEntities.BaseCardBinRangeDO::getBinLength)
                        .orderByDesc(CardBinEntities.BaseCardBinRangeDO::getUpdateTime)
                        .orderByDesc(CardBinEntities.BaseCardBinRangeDO::getId)
        );
        DictLabels labels = loadDictLabels();
        List<CardBinDTOs.CardBinResponse> matches = rows.stream().map(row -> toResponse(row, labels)).toList();
        CardBinDTOs.CardBinMatchResponse response = new CardBinDTOs.CardBinMatchResponse();
        response.setMatched(!matches.isEmpty());
        response.setMatchCount(matches.size());
        response.setBestMatch(matches.isEmpty() ? null : matches.get(0));
        response.setMatches(matches);
        return response;
    }

    /**
     * 查询导入批次列表。
     *
     * @param request 分页请求
     * @return 导入批次分页数据
     */
    public PageResult<CardBinDTOs.CardBinImportBatchResponse> importBatches(CardBinDTOs.CardBinQueryRequest request) {
        CardBinDTOs.CardBinQueryRequest query = request == null ? new CardBinDTOs.CardBinQueryRequest() : request;
        Page<CardBinEntities.BaseCardBinImportBatchDO> page = importBatchMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<CardBinEntities.BaseCardBinImportBatchDO>lambdaQuery()
                        .orderByDesc(CardBinEntities.BaseCardBinImportBatchDO::getCreateTime)
                        .orderByDesc(CardBinEntities.BaseCardBinImportBatchDO::getId)
        );
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream().map(this::toBatchResponse).toList()
        );
    }

    /**
     * 从旧卡 BIN 表初始化导入数据。
     *
     * @return 本次导入批次结果
     */
    @Transactional(rollbackFor = Exception.class)
    @DS(DataSourceName.MASTER)
    public CardBinDTOs.CardBinImportBatchResponse initFromLegacyDb() {
        String batchNo = "INIT_DB_IMPORT_" + BATCH_TIME_FORMATTER.format(LocalDateTime.now());
        CardBinEntities.BaseCardBinImportBatchDO batch = new CardBinEntities.BaseCardBinImportBatchDO();
        batch.setBatchNo(batchNo);
        batch.setImportType(IMPORT_TYPE_DB_INIT);
        batch.setDataSource(DATA_SOURCE_LEGACY);
        batch.setTotalCount(0);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setConflictCount(0);
        batch.setDuplicateCount(0);
        batch.setStatus(0);
        batch.setRemark("从旧表 card_bin_type_info 初始化导入");
        batch.setCreateBy(currentOperatorName());
        batch.setCreateTime(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());
        importBatchMapper.insert(batch);

        if (cardBinRangeMapper.countTable("card_bin_type_info") <= 0) {
            batch.setStatus(3);
            batch.setErrorMessage("旧表 card_bin_type_info 不存在");
            batch.setUpdateTime(LocalDateTime.now());
            importBatchMapper.updateById(batch);
            throw badRequest("旧表 card_bin_type_info 不存在，无法初始化导入");
        }

        int totalCount = cardBinRangeMapper.countLegacyRows();
        int failedCount = cardBinRangeMapper.countInvalidLegacyRows();
        int successCount = 0;
        int duplicateCount = 0;
        cacheInvalidationCoordinator.prepare();
        for (Map<String, Object> legacy : cardBinRangeMapper.selectValidLegacyRows()) {
            Long legacyPkId = longValue(legacy.get("legacyPkId"));
            if (legacyPkId == null || existsLegacyRow(legacyPkId)) {
                duplicateCount++;
                continue;
            }
            CardBinEntities.BaseCardBinRangeDO row = toRangeFromLegacy(legacy, batchNo);
            cardBinRangeMapper.insert(row);
            successCount++;
        }
        batch.setTotalCount(totalCount);
        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setDuplicateCount(duplicateCount);
        batch.setStatus(successCount == 0 && totalCount > 0 ? 3 : (failedCount > 0 || duplicateCount > 0 ? 2 : 1));
        batch.setUpdateTime(LocalDateTime.now());
        importBatchMapper.updateById(batch);
        return toBatchResponse(batch);
    }

    /**
     * 导出卡 BIN 区间。
     *
     * @param request 查询请求
     * @param response HTTP 响应
     */
    @DS(DataSourceName.SLAVE)
    public void export(CardBinDTOs.CardBinQueryRequest request, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        DictLabels labels = loadDictLabels();
        List<CardBinExportRow> rows = cardBinRangeMapper.selectList(buildListQuery(request == null ? new CardBinDTOs.CardBinQueryRequest() : request))
                .stream()
                .map(row -> toExportRow(row, labels))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<CardBinExportRow>builder()
                        .fileName(excelI18nMessageResolver.resolve("excel.cardBin.title", locale) + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName(excelI18nMessageResolver.resolve("excel.cardBin.title", locale))
                        .titleKey("excel.cardBin.title")
                        .operator(currentOperatorName())
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(excelI18nMessageResolver.resolve("excel.common.noCondition", locale))
                        .rowClass(CardBinExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 查询卡 BIN 页面选项。
     *
     * @return 页面下拉选项聚合响应
     */
    @DS(DataSourceName.SLAVE)
    public CardBinDTOs.CardBinOptionsResponse options() {
        CardBinDTOs.CardBinOptionsResponse response = new CardBinDTOs.CardBinOptionsResponse();
        response.setCardBrandOptions(dictOptions(CARD_BRAND_DICT));
        response.setCardTypeOptions(dictOptions(CARD_TYPE_DICT));
        response.setStatusOptions(dictOptions(CARD_BIN_STATUS_DICT));
        response.setDataSourceOptions(dictOptions(DATA_SOURCE_DICT));
        response.setCountryOptions(countryOptions());
        return response;
    }

    /**
     * 构造listquery对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
     */
    private LambdaQueryWrapper<CardBinEntities.BaseCardBinRangeDO> buildListQuery(CardBinDTOs.CardBinQueryRequest query) {
        LambdaQueryWrapper<CardBinEntities.BaseCardBinRangeDO> wrapper = Wrappers.<CardBinEntities.BaseCardBinRangeDO>lambdaQuery()
                .eq(CardBinEntities.BaseCardBinRangeDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(query.getCardBrand()), CardBinEntities.BaseCardBinRangeDO::getCardBrand, trimToNull(query.getCardBrand()))
                .eq(StringUtils.hasText(query.getCardType()), CardBinEntities.BaseCardBinRangeDO::getCardType, trimToNull(query.getCardType()))
                .eq(StringUtils.hasText(query.getIssuerCountryAlpha2()), CardBinEntities.BaseCardBinRangeDO::getIssuerCountryAlpha2, upper(trimToNull(query.getIssuerCountryAlpha2())))
                .like(StringUtils.hasText(query.getIssuerBank()), CardBinEntities.BaseCardBinRangeDO::getIssuerBank, trimToNull(query.getIssuerBank()))
                .eq(query.getStatus() != null, CardBinEntities.BaseCardBinRangeDO::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getDataSource()), CardBinEntities.BaseCardBinRangeDO::getDataSource, trimToNull(query.getDataSource()));
        if (StringUtils.hasText(query.getCardBin())) {
            NormalizedBinRange range = normalizeRange(query.getCardBin(), null);
            wrapper.le(CardBinEntities.BaseCardBinRangeDO::getCardBinStart, range.end())
                    .ge(CardBinEntities.BaseCardBinRangeDO::getCardBinEnd, range.start());
        }
        return wrapper.orderByDesc(CardBinEntities.BaseCardBinRangeDO::getUpdateTime)
                .orderByDesc(CardBinEntities.BaseCardBinRangeDO::getId);
    }

/**
 * 构造savefields对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param range range 输入值，参与 范围 的查询、校验、转换、写入或日志摘要
 * @param dataSource data Source 输入值，参与 data来源 的查询、校验、转换、写入或日志摘要
 * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
 */
    private void fillSaveFields(CardBinEntities.BaseCardBinRangeDO row,
                                CardBinDTOs.CardBinSaveRequest request,
                                NormalizedBinRange range,
                                String dataSource,
                                Integer status) {
        row.setCardBinStart(range.start());
        row.setCardBinEnd(range.end());
        row.setBinLength(range.length());
        row.setCardBrand(upper(request.getCardBrand()));
        row.setCardSubBrand(trimToNull(request.getCardSubBrand()));
        row.setCardType(upper(request.getCardType()));
        row.setCardLevel(trimToNull(request.getCardLevel()));
        row.setIssuerCountryName(trimToNull(request.getIssuerCountryName()));
        row.setIssuerCountryAlpha2(upper(trimToNull(request.getIssuerCountryAlpha2())));
        row.setIssuerCountryAlpha3(upper(trimToNull(request.getIssuerCountryAlpha3())));
        row.setIssuerCountryNumeric(trimToNull(request.getIssuerCountryNumeric()));
        row.setIssuerBank(trimToNull(request.getIssuerBank()));
        row.setIssuerWebUrl(trimToNull(request.getIssuerWebUrl()));
        row.setIssuerTelephone(trimToNull(request.getIssuerTelephone()));
        row.setDataSource(upper(dataSource));
        row.setSourcePriority(request.getSourcePriority() == null ? DEFAULT_PRIORITY : request.getSourcePriority());
        row.setEffectiveTime(request.getEffectiveTime());
        row.setExpireTime(request.getExpireTime());
        row.setStatus(status);
        row.setRemark(trimToNull(request.getRemark()));
    }

    /**
     * 整理adjust状态按conflicts，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param currentId current ID 输入值，参与 当前ID 的查询、校验、转换、写入或日志摘要
     * @param range range 输入值，参与 范围 的查询、校验、转换、写入或日志摘要
     * @param requestedStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Integer adjustStatusByConflicts(Long currentId, NormalizedBinRange range, Integer requestedStatus) {
        List<CardBinEntities.BaseCardBinRangeDO> conflicts = cardBinRangeMapper.selectList(
                Wrappers.<CardBinEntities.BaseCardBinRangeDO>lambdaQuery()
                        .eq(CardBinEntities.BaseCardBinRangeDO::getDeleted, NOT_DELETED)
                        .in(CardBinEntities.BaseCardBinRangeDO::getStatus, List.of(STATUS_ENABLED, STATUS_PENDING))
                        .ne(currentId != null, CardBinEntities.BaseCardBinRangeDO::getId, currentId)
                        .le(CardBinEntities.BaseCardBinRangeDO::getCardBinStart, range.end())
                        .ge(CardBinEntities.BaseCardBinRangeDO::getCardBinEnd, range.start())
        );
        boolean containedOverlap = false;
        for (CardBinEntities.BaseCardBinRangeDO conflict : conflicts) {
            long start = conflict.getCardBinStart();
            long end = conflict.getCardBinEnd();
            if (start == range.start() && end == range.end()) {
                throw badRequest("卡 BIN 区间已存在，请勿重复维护");
            }
            boolean newContainsOld = range.start() <= start && range.end() >= end;
            boolean oldContainsNew = start <= range.start() && end >= range.end();
            if (newContainsOld || oldContainsNew) {
                containedOverlap = true;
                continue;
            }
            throw badRequest("卡 BIN 区间与现有数据部分重叠，请调整起止值");
        }
        if (containedOverlap && requestedStatus == STATUS_ENABLED) {
            return STATUS_PENDING;
        }
        return requestedStatus;
    }

    /**
     * 解析normalize范围，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param start start 输入值，参与 start 的查询、校验、转换、写入或日志摘要
     * @param end end 输入值，参与 end 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private NormalizedBinRange normalizeRange(String start, String end) {
        String normalizedStart = validateBin(start, "CardBin 仅允许输入 6 到 11 位数字，不允许输入完整卡号。");
        String normalizedEnd = StringUtils.hasText(end) ? validateBin(end, "CardBin 仅允许输入 6 到 11 位数字，不允许输入完整卡号。") : normalizedStart;
        if (normalizedStart.length() != normalizedEnd.length()) {
            throw badRequest("BIN 起始值和结束值长度必须一致");
        }
        long startValue = normalizeStartValue(normalizedStart);
        long endValue = normalizeEndValue(normalizedEnd);
        if (startValue > endValue) {
            throw badRequest("BIN 起始值不能大于结束值");
        }
        return new NormalizedBinRange(startValue, endValue, normalizedStart.length());
    }

    /**
     * 校验BIN输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String validateBin(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null || !normalized.matches("^[0-9]{6,11}$")) {
            throw badRequest(message);
        }
        return normalized;
    }

    /**
     * 解析normalizestart值，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private long normalizeStartValue(String value) {
        return Long.parseLong(value + "0".repeat(NORMALIZED_BIN_LENGTH - value.length()));
    }

    /**
     * 解析normalizeend值，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private long normalizeEndValue(String value) {
        return Long.parseLong(value + "9".repeat(NORMALIZED_BIN_LENGTH - value.length()));
    }

    /**
     * 整理BINto展示，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String binToDisplay(Long value) {
        if (value == null) {
            return null;
        }
        return String.format("%011d", value);
    }

    /**
     * 解析normalize状态，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param defaultStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 构造、转换或解析后的业务值
     */
    private Integer normalizeStatus(Integer status, Integer defaultStatus) {
        Integer target = status == null ? defaultStatus : status;
        if (target == null || !VALID_STATUSES.contains(target)) {
            throw badRequest("状态只支持 0、1、2、3");
        }
        return target;
    }

    /**
     * 校验断言状态transition输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param currentStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param targetStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     */
    private void assertStatusTransition(Integer currentStatus, Integer targetStatus) {
        if (Objects.equals(currentStatus, targetStatus)) {
            return;
        }
        boolean allowed = switch (currentStatus == null ? STATUS_DISABLED : currentStatus) {
            case STATUS_ENABLED -> Set.of(STATUS_DISABLED, STATUS_PENDING, STATUS_EXPIRED).contains(targetStatus);
            case STATUS_DISABLED -> Set.of(STATUS_ENABLED, STATUS_PENDING).contains(targetStatus);
            case STATUS_PENDING -> Set.of(STATUS_ENABLED, STATUS_DISABLED).contains(targetStatus);
            case STATUS_EXPIRED -> Set.of(STATUS_DISABLED, STATUS_ENABLED).contains(targetStatus);
            default -> false;
        };
        if (!allowed) {
            throw badRequest("当前状态不允许变更为目标状态");
        }
    }

    /**
     * 校验断言dictvalue输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param dictType dict Type 输入值，参与 dicttype 的查询、校验、转换、写入或日志摘要
     * @param dictValue dict Value 输入值，参与 dictvalue 的查询、校验、转换、写入或日志摘要
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void assertDictValue(String dictType, String dictValue, String message) {
        String value = upper(trimToNull(dictValue));
        if (value == null) {
            throw badRequest(message);
        }
        boolean exists = listDict(dictType).stream()
                .anyMatch(item -> value.equalsIgnoreCase(item.getDictValue()));
        if (!exists) {
            throw badRequest(message + ": " + value);
        }
    }

    /**
     * 查询字典数据，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param dictType dict Type 输入值，参与 dicttype 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<SysDictDataDTO> listDict(String dictType) {
        SysDictDataQueryRequest request = new SysDictDataQueryRequest();
        request.setDictType(dictType);
        request.setLocale(DEFAULT_LOCALE);
        request.setStatus(AuthConstants.ENABLED);
        request.setPageSize(500);
        return adminDictService.listDictData(request);
    }

    /**
     * 规范化dictoptions，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param dictType dict Type 输入值，参与 dicttype 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private List<CardBinDTOs.CardBinOption> dictOptions(String dictType) {
        return listDict(dictType).stream()
                .map(item -> {
                    CardBinDTOs.CardBinOption option = option(item.getDictLabel(), item.getDictValue());
                    option.setExtraJson(item.getExtraJson());
                    return option;
                })
                .toList();
    }

    /**
     * 统计countryoptions，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private List<CardBinDTOs.CardBinOption> countryOptions() {
        return isoCountryMapper.selectList(Wrappers.<IsoCountryDO>lambdaQuery()
                        .eq(IsoCountryDO::getDeleted, 0)
                        .eq(IsoCountryDO::getStatus, AuthConstants.ENABLED)
                        .orderByAsc(IsoCountryDO::getAlpha2Code))
                .stream()
                .map(country -> {
                    CardBinDTOs.CardBinOption option = new CardBinDTOs.CardBinOption();
                    String name = firstText(country.getChineseName(), country.getEnglishName(), country.getShortEnglishName(), country.getAlpha2Code());
                    option.setLabel(name + "（" + country.getAlpha2Code() + "）");
                    option.setValue(country.getAlpha2Code());
                    option.setAlpha2(country.getAlpha2Code());
                    option.setAlpha3(country.getAlpha3Code());
                    option.setNumeric(country.getNumericCode());
                    option.setCountryName(firstText(country.getChineseName(), country.getEnglishName(), country.getShortEnglishName()));
                    option.setFlagEmoji(country.getFlagEmoji());
                    return option;
                })
                .toList();
    }

    /**
     * 规范化option，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param label label 输入值，参与 label 的查询、校验、转换、写入或日志摘要
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private CardBinDTOs.CardBinOption option(String label, String value) {
        CardBinDTOs.CardBinOption option = new CardBinDTOs.CardBinOption();
        option.setLabel(label);
        option.setValue(value);
        return option;
    }

    /**
     * 查询字典标签，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private DictLabels loadDictLabels() {
        return new DictLabels(
                labels(CARD_BRAND_DICT),
                labels(CARD_TYPE_DICT),
                labels(CARD_BIN_STATUS_DICT),
                labels(DATA_SOURCE_DICT)
        );
    }

    /**
     * 规范化labels，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param dictType dict Type 输入值，参与 dicttype 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, String> labels(String dictType) {
        return listDict(dictType).stream()
                .collect(Collectors.toMap(
                        item -> upper(item.getDictValue()),
                        SysDictDataDTO::getDictLabel,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    /**
     * 构造响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param labels labels 输入值，参与 labels 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private CardBinDTOs.CardBinResponse toResponse(CardBinEntities.BaseCardBinRangeDO row, DictLabels labels) {
        CardBinDTOs.CardBinResponse response = new CardBinDTOs.CardBinResponse();
        response.setId(row.getId());
        response.setLegacyPkId(row.getLegacyPkId());
        response.setCardBinStart(binToDisplay(row.getCardBinStart()));
        response.setCardBinEnd(binToDisplay(row.getCardBinEnd()));
        response.setBinLength(row.getBinLength());
        response.setCardBrand(row.getCardBrand());
        response.setCardBrandName(labels.cardBrands().getOrDefault(upper(row.getCardBrand()), row.getCardBrand()));
        response.setCardSubBrand(row.getCardSubBrand());
        response.setCardType(row.getCardType());
        response.setCardTypeName(labels.cardTypes().getOrDefault(upper(row.getCardType()), row.getCardType()));
        response.setCardLevel(row.getCardLevel());
        response.setIssuerCountryName(row.getIssuerCountryName());
        response.setIssuerCountryAlpha2(row.getIssuerCountryAlpha2());
        response.setIssuerCountryAlpha3(row.getIssuerCountryAlpha3());
        response.setIssuerCountryNumeric(row.getIssuerCountryNumeric());
        response.setIssuerBank(row.getIssuerBank());
        response.setIssuerWebUrl(row.getIssuerWebUrl());
        response.setIssuerTelephone(row.getIssuerTelephone());
        response.setDataSource(row.getDataSource());
        response.setDataSourceName(labels.dataSources().getOrDefault(upper(row.getDataSource()), row.getDataSource()));
        response.setSourceBatchNo(row.getSourceBatchNo());
        response.setSourcePriority(row.getSourcePriority());
        response.setEffectiveTime(row.getEffectiveTime());
        response.setExpireTime(row.getExpireTime());
        response.setStatus(row.getStatus());
        response.setStatusName(labels.statuses().getOrDefault(String.valueOf(row.getStatus()), String.valueOf(row.getStatus())));
        response.setRemark(row.getRemark());
        response.setCreateBy(row.getCreateBy());
        response.setUpdateBy(row.getUpdateBy());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    /**
     * 构造exportrow对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param labels labels 输入值，参与 labels 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private CardBinExportRow toExportRow(CardBinEntities.BaseCardBinRangeDO row, DictLabels labels) {
        CardBinDTOs.CardBinResponse response = toResponse(row, labels);
        CardBinExportRow exportRow = new CardBinExportRow();
        exportRow.setCardBinStart(response.getCardBinStart());
        exportRow.setCardBinEnd(response.getCardBinEnd());
        exportRow.setBinLength(response.getBinLength());
        exportRow.setCardBrand(response.getCardBrandName());
        exportRow.setCardSubBrand(response.getCardSubBrand());
        exportRow.setCardType(response.getCardTypeName());
        exportRow.setCardLevel(response.getCardLevel());
        exportRow.setIssuerCountryName(response.getIssuerCountryName());
        exportRow.setIssuerCountryAlpha2(response.getIssuerCountryAlpha2());
        exportRow.setIssuerCountryAlpha3(response.getIssuerCountryAlpha3());
        exportRow.setIssuerCountryNumeric(response.getIssuerCountryNumeric());
        exportRow.setIssuerBank(response.getIssuerBank());
        exportRow.setIssuerWebUrl(response.getIssuerWebUrl());
        exportRow.setIssuerTelephone(response.getIssuerTelephone());
        exportRow.setDataSource(response.getDataSourceName());
        exportRow.setStatus(response.getStatusName());
        exportRow.setEffectiveTime(response.getEffectiveTime());
        exportRow.setExpireTime(response.getExpireTime());
        exportRow.setRemark(response.getRemark());
        exportRow.setCreateBy(response.getCreateBy());
        exportRow.setUpdateBy(response.getUpdateBy());
        exportRow.setCreateTime(response.getCreateTime());
        exportRow.setUpdateTime(response.getUpdateTime());
        return exportRow;
    }

    /**
     * 构造batch响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
    private CardBinDTOs.CardBinImportBatchResponse toBatchResponse(CardBinEntities.BaseCardBinImportBatchDO row) {
        CardBinDTOs.CardBinImportBatchResponse response = new CardBinDTOs.CardBinImportBatchResponse();
        response.setId(row.getId());
        response.setBatchNo(row.getBatchNo());
        response.setImportType(row.getImportType());
        response.setDataSource(row.getDataSource());
        response.setFileName(row.getFileName());
        response.setTotalCount(row.getTotalCount());
        response.setSuccessCount(row.getSuccessCount());
        response.setFailedCount(row.getFailedCount());
        response.setConflictCount(row.getConflictCount());
        response.setDuplicateCount(row.getDuplicateCount());
        response.setStatus(row.getStatus());
        response.setErrorMessage(row.getErrorMessage());
        response.setRemark(row.getRemark());
        response.setCreateBy(row.getCreateBy());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    /**
     * 判断 exists legacy row 条件是否成立，用于控制 Admin Base Card Bin Application Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param legacyPkId legacy Pk ID 输入值，参与 legacypkID 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean existsLegacyRow(Long legacyPkId) {
        Long count = cardBinRangeMapper.selectCount(
                Wrappers.<CardBinEntities.BaseCardBinRangeDO>lambdaQuery()
                        .eq(CardBinEntities.BaseCardBinRangeDO::getDeleted, NOT_DELETED)
                        .eq(CardBinEntities.BaseCardBinRangeDO::getDataSource, DATA_SOURCE_LEGACY)
                        .eq(CardBinEntities.BaseCardBinRangeDO::getLegacyPkId, legacyPkId)
        );
        return count != null && count > 0;
    }

    /**
     * 构造范围fromlegacy对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param legacy legacy 输入值，参与 legacy 的查询、校验、转换、写入或日志摘要
     * @param batchNo batch No 输入值，参与 batchno 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private CardBinEntities.BaseCardBinRangeDO toRangeFromLegacy(Map<String, Object> legacy, String batchNo) {
        Long start = longValue(legacy.get("cardBinStart"));
        Long end = longValue(legacy.get("cardBinEnd"));
        CardBinEntities.BaseCardBinRangeDO row = new CardBinEntities.BaseCardBinRangeDO();
        row.setLegacyPkId(longValue(legacy.get("legacyPkId")));
        row.setCardBinStart(start);
        row.setCardBinEnd(end);
        row.setBinLength(inferBinLength(start, end));
        row.setCardBrand(defaultIfBlank(upper(stringValue(legacy.get("cardBrand"))), "UNKNOWN"));
        row.setCardSubBrand(trimToNull(stringValue(legacy.get("cardSubBrand"))));
        row.setCardType(normalizeLegacyCardType(stringValue(legacy.get("creditDebit"))));
        row.setIssuerCountryName(trimToNull(stringValue(legacy.get("issuerCountryName"))));
        row.setIssuerCountryAlpha2(upper(trimToNull(stringValue(legacy.get("issuerCountryAlpha2")))));
        row.setIssuerCountryAlpha3(upper(trimToNull(stringValue(legacy.get("issuerCountryAlpha3")))));
        row.setIssuerCountryNumeric(trimToNull(stringValue(legacy.get("issuerCountryNumeric"))));
        row.setIssuerBank(trimToNull(stringValue(legacy.get("issuerBank"))));
        row.setIssuerWebUrl(trimToNull(stringValue(legacy.get("issuerWebUrl"))));
        row.setIssuerTelephone(trimToNull(stringValue(legacy.get("issuerTelephone"))));
        row.setDataSource(DATA_SOURCE_LEGACY);
        row.setSourceBatchNo(batchNo);
        row.setSourcePriority(DEFAULT_PRIORITY);
        row.setStatus(STATUS_ENABLED);
        row.setRemark("旧表card_bin_type_info初始化导入");
        row.setCreateBy(currentOperatorName());
        row.setUpdateBy(currentOperatorName());
        row.setCreateTime(localDateTimeValue(legacy.get("createTime")));
        row.setUpdateTime(localDateTimeValue(legacy.get("updateTime")));
        row.setDeleted(NOT_DELETED);
        return row;
    }

    /**
     * 规范化inferBINlength，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param start start 输入值，参与 start 的查询、校验、转换、写入或日志摘要
     * @param end end 输入值，参与 end 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int inferBinLength(Long start, Long end) {
        if (start == null || end == null) {
            return NORMALIZED_BIN_LENGTH;
        }
        for (int length = 6; length <= NORMALIZED_BIN_LENGTH; length++) {
            long factor = (long) Math.pow(10, NORMALIZED_BIN_LENGTH - length);
            if (factor <= 1) {
                return NORMALIZED_BIN_LENGTH;
            }
            if (start % factor == 0 && end % factor == factor - 1 && start / factor == end / factor) {
                return length;
            }
        }
        return NORMALIZED_BIN_LENGTH;
    }

    /**
     * 解析normalizelegacycardtype，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeLegacyCardType(String value) {
        String normalized = upper(trimToNull(value));
        if (normalized == null) {
            return "UNKNOWN";
        }
        return switch (normalized) {
            case "CREDIT", "DEBIT", "PREPAID", "CHARGE", "COMMERCIAL" -> normalized;
            default -> "UNKNOWN";
        };
    }

    /**
     * 整理long值，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.parseLong(value.toString());
    }

    /**
     * 整理string值，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 整理localdate时间值，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LocalDateTime localDateTimeValue(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return LocalDateTime.now();
    }

    /**
     * 查询生效记录，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private CardBinEntities.BaseCardBinRangeDO getActiveRow(Long id) {
        if (id == null) {
            throw badRequest("id is required");
        }
        CardBinEntities.BaseCardBinRangeDO row = cardBinRangeMapper.selectOne(
                Wrappers.<CardBinEntities.BaseCardBinRangeDO>lambdaQuery()
                        .eq(CardBinEntities.BaseCardBinRangeDO::getId, id)
                        .eq(CardBinEntities.BaseCardBinRangeDO::getDeleted, NOT_DELETED)
        );
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "卡 BIN 数据不存在");
        }
        return row;
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
            return "admin";
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount() : "admin";
    }

    /**
     * 整理首个非空文本，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param values values 输入值，参与 values 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * 整理默认ifblank，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param defaultValue default Value 输入值，参与 默认value 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String defaultIfBlank(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
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
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 规范化upper，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
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
        return new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), message);
    }

    private record NormalizedBinRange(long start, long end, int length) {
    }

    private record DictLabels(Map<String, String> cardBrands,
                              Map<String, String> cardTypes,
                              Map<String, String> statuses,
                              Map<String, String> dataSources) {
    }
}
