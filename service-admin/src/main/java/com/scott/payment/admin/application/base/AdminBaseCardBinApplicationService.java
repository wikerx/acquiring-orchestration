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
 * 管理端卡 BIN应用服务，用于运营后台，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 */
@Service
public class AdminBaseCardBinApplicationService {

    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter BATCH_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long NOT_DELETED = AuthConstants.NOT_DELETED;
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_PENDING = 2;
    private static final int STATUS_EXPIRED = 3;
    private static final int DEFAULT_PRIORITY = 50;
    private static final int NORMALIZED_BIN_LENGTH = 11;
    private static final String CARD_BRAND_DICT = "card_brand";
    private static final String CARD_TYPE_DICT = "base_card_type";
    private static final String CARD_BIN_STATUS_DICT = "base_card_bin_status";
    private static final String DATA_SOURCE_DICT = "base_card_bin_data_source";
    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final String DATA_SOURCE_MANUAL = "MANUAL";
    private static final String DATA_SOURCE_LEGACY = "LEGACY_DB";
    private static final String IMPORT_TYPE_DB_INIT = "DB_INIT";
    private static final Set<Integer> VALID_STATUSES = Set.of(STATUS_DISABLED, STATUS_ENABLED, STATUS_PENDING, STATUS_EXPIRED);

    private final BaseCardBinRangeMapper cardBinRangeMapper;
    private final BaseCardBinImportBatchMapper importBatchMapper;
    private final AdminDictService adminDictService;
    private final IsoCountryMapper isoCountryMapper;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * Card BIN 数据变更与 Redis generation、可靠 MQ 的事务协调器。
     */
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
        cacheInvalidationCoordinator.prepare();
        int successCount = cardBinRangeMapper.insertValidLegacyRows(batchNo, currentOperatorName());
        int validCount = Math.max(0, totalCount - failedCount);
        int duplicateCount = Math.max(0, validCount - successCount);
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
     * 构建列表查询。
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
     * 将保存请求中的业务字段填充到卡 BIN 实体。
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
     * 根据冲突记录调整卡 BIN 导入结果状态。
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
     * 校验并规范化卡 BIN 起止范围。
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
     * 校验 BIN。
     */
    private String validateBin(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null || !normalized.matches("^[0-9]{6,11}$")) {
            throw badRequest(message);
        }
        return normalized;
    }

    /**
     * 将卡 BIN 起始值补齐为固定长度数字串。
     */
    private long normalizeStartValue(String value) {
        return Long.parseLong(value + "0".repeat(NORMALIZED_BIN_LENGTH - value.length()));
    }

    /**
     * 规范化完成值。
     */
    private long normalizeEndValue(String value) {
        return Long.parseLong(value + "9".repeat(NORMALIZED_BIN_LENGTH - value.length()));
    }

    /**
     * 将卡 BIN 规范化为管理端展示文本。
     */
    private String binToDisplay(Long value) {
        if (value == null) {
            return null;
        }
        return String.format("%011d", value);
    }

    /**
     * 规范化状态。
     */
    private Integer normalizeStatus(Integer status, Integer defaultStatus) {
        Integer target = status == null ? defaultStatus : status;
        if (target == null || !VALID_STATUSES.contains(target)) {
            throw badRequest("状态只支持 0、1、2、3");
        }
        return target;
    }

    /**
     * 校验卡 BIN 状态变更符合状态机约束。
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
     * 读取并校验数据字典值。
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
     * 查询指定类型的启用数据字典项。
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
     * 将数据字典条目转换为下拉选项。
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
     * 查询国家或地区下拉选项。
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
     * 构建管理端下拉选项。
     */
    private CardBinDTOs.CardBinOption option(String label, String value) {
        CardBinDTOs.CardBinOption option = new CardBinDTOs.CardBinOption();
        option.setLabel(label);
        option.setValue(value);
        return option;
    }

    /**
     * 批量加载数据字典展示标签。
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
     * 批量读取数据字典展示标签。
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
     * 将卡 BIN 持久化记录转换为管理端响应。
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
     * 转换为导出行数据。
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
     * 将卡 BIN 导入批次实体转换为管理端响应。
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
     * 查询当前生效的卡 BIN 记录。
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
     * 从可信认证上下文解析当前操作人名称。
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
     * 返回首个非空文本。
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
     * 在文本为空时返回默认值。
     */
    private String defaultIfBlank(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    /**
     * 去除文本首尾空白并把空串转换为 null。
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 将业务编码规范化为大写文本。
     */
    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 构建参数校验失败异常。
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), message);
    }

    /**
     * 封装规范化后的卡 BIN 起止区间。
     */
    private record NormalizedBinRange(long start, long end, int length) {
    }

    /**
     * 封装数据字典编码与展示标签索引。
     */
    private record DictLabels(Map<String, String> cardBrands,
                              Map<String, String> cardTypes,
                              Map<String, String> statuses,
                              Map<String, String> dataSources) {
    }
}
