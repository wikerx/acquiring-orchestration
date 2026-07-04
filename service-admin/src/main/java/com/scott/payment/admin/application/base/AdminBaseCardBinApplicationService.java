package com.scott.payment.admin.application.base;

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

    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
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
                                              ExcelLocaleResolver excelLocaleResolver) {
        this.cardBinRangeMapper = cardBinRangeMapper;
        this.importBatchMapper = importBatchMapper;
        this.adminDictService = adminDictService;
        this.isoCountryMapper = isoCountryMapper;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 分页查询卡 BIN 区间。
     *
     * @param request 查询请求
     * @return 卡 BIN 分页数据
     */
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
        cardBinRangeMapper.updateById(row);
        return toResponse(row, loadDictLabels());
    }

    /**
     * 逻辑删除卡 BIN 区间。
     *
     * @param id 主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        CardBinEntities.BaseCardBinRangeDO row = getActiveRow(id);
        row.setStatus(STATUS_DISABLED);
        row.setDeleted(row.getId());
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
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
    public CardBinDTOs.CardBinResponse updateStatus(Long id, CardBinDTOs.CardBinStatusRequest request) {
        CardBinEntities.BaseCardBinRangeDO row = getActiveRow(id);
        Integer targetStatus = normalizeStatus(request.getStatus(), null);
        assertStatusTransition(row.getStatus(), targetStatus);
        row.setStatus(targetStatus);
        row.setUpdateBy(currentOperatorName());
        row.setUpdateTime(LocalDateTime.now());
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
                        .orderByDesc(CardBinEntities.BaseCardBinRangeDO::getSourcePriority)
                        .orderByDesc(CardBinEntities.BaseCardBinRangeDO::getUpdateTime)
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
    public void export(CardBinDTOs.CardBinQueryRequest request, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        DictLabels labels = loadDictLabels();
        List<CardBinExportRow> rows = cardBinRangeMapper.selectList(buildListQuery(request == null ? new CardBinDTOs.CardBinQueryRequest() : request))
                .stream()
                .map(row -> toExportRow(row, labels))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<CardBinExportRow>builder()
                        .fileName("卡BIN库_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName("卡BIN库")
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
    public CardBinDTOs.CardBinOptionsResponse options() {
        CardBinDTOs.CardBinOptionsResponse response = new CardBinDTOs.CardBinOptionsResponse();
        response.setCardBrandOptions(dictOptions(CARD_BRAND_DICT));
        response.setCardTypeOptions(dictOptions(CARD_TYPE_DICT));
        response.setStatusOptions(dictOptions(CARD_BIN_STATUS_DICT));
        response.setDataSourceOptions(dictOptions(DATA_SOURCE_DICT));
        response.setCountryOptions(countryOptions());
        return response;
    }

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

    private String validateBin(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null || !normalized.matches("^[0-9]{6,11}$")) {
            throw badRequest(message);
        }
        return normalized;
    }

    private long normalizeStartValue(String value) {
        return Long.parseLong(value + "0".repeat(NORMALIZED_BIN_LENGTH - value.length()));
    }

    private long normalizeEndValue(String value) {
        return Long.parseLong(value + "9".repeat(NORMALIZED_BIN_LENGTH - value.length()));
    }

    private String binToDisplay(Long value) {
        if (value == null) {
            return null;
        }
        return String.format("%011d", value);
    }

    private Integer normalizeStatus(Integer status, Integer defaultStatus) {
        Integer target = status == null ? defaultStatus : status;
        if (target == null || !VALID_STATUSES.contains(target)) {
            throw badRequest("状态只支持 0、1、2、3");
        }
        return target;
    }

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

    private List<SysDictDataDTO> listDict(String dictType) {
        SysDictDataQueryRequest request = new SysDictDataQueryRequest();
        request.setDictType(dictType);
        request.setLocale(DEFAULT_LOCALE);
        request.setStatus(AuthConstants.ENABLED);
        request.setPageSize(500);
        return adminDictService.listDictData(request);
    }

    private List<CardBinDTOs.CardBinOption> dictOptions(String dictType) {
        return listDict(dictType).stream()
                .map(item -> {
                    CardBinDTOs.CardBinOption option = option(item.getDictLabel(), item.getDictValue());
                    option.setExtraJson(item.getExtraJson());
                    return option;
                })
                .toList();
    }

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

    private CardBinDTOs.CardBinOption option(String label, String value) {
        CardBinDTOs.CardBinOption option = new CardBinDTOs.CardBinOption();
        option.setLabel(label);
        option.setValue(value);
        return option;
    }

    private DictLabels loadDictLabels() {
        return new DictLabels(
                labels(CARD_BRAND_DICT),
                labels(CARD_TYPE_DICT),
                labels(CARD_BIN_STATUS_DICT),
                labels(DATA_SOURCE_DICT)
        );
    }

    private Map<String, String> labels(String dictType) {
        return listDict(dictType).stream()
                .collect(Collectors.toMap(
                        item -> upper(item.getDictValue()),
                        SysDictDataDTO::getDictLabel,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

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

    private boolean existsLegacyRow(Long legacyPkId) {
        Long count = cardBinRangeMapper.selectCount(
                Wrappers.<CardBinEntities.BaseCardBinRangeDO>lambdaQuery()
                        .eq(CardBinEntities.BaseCardBinRangeDO::getDeleted, NOT_DELETED)
                        .eq(CardBinEntities.BaseCardBinRangeDO::getDataSource, DATA_SOURCE_LEGACY)
                        .eq(CardBinEntities.BaseCardBinRangeDO::getLegacyPkId, legacyPkId)
        );
        return count != null && count > 0;
    }

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

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.parseLong(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private LocalDateTime localDateTimeValue(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return LocalDateTime.now();
    }

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

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

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
