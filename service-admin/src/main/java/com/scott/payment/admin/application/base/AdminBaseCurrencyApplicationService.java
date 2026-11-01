package com.scott.payment.admin.application.base;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.converter.IsoCurrencyConverter;
import com.scott.payment.admin.dto.base.IsoCurrencyDTOs.Response;
import com.scott.payment.admin.dto.base.IsoCurrencyDTOs.SaveRequest;
import com.scott.payment.admin.dto.export.IsoCurrencyExportRow;
import com.scott.payment.admin.dto.base.IsoStatusUpdateRequest;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.iso.IsoCurrencyIconKey;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import com.scott.payment.component.db.iso.service.IsoDictionaryCacheInvalidator;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 币种基础资料应用服务
 *
 * <p>负责管理后台币种基础资料用例编排，包括分页查询、详情查询、新增、更新、状态切换和逻辑删除。</p>
 */
@Service
public class AdminBaseCurrencyApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 默认启用状态。
     */
    private static final int DEFAULT_ENABLED_STATUS = 1;

    /**
     * 逻辑未删除标记。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 逻辑已删除标记。
     */
    private static final int DELETED = 1;

    /**
     * 币种数据访问组件。
     */
    private final IsoCurrencyMapper isoCurrencyMapper;

    /**
     * 币种接口模型转换器。
     */
    private final IsoCurrencyConverter isoCurrencyConverter;

    /**
     * ISO 字典缓存失效器。写操作在主库事务内登记全量、代码和展示快照失效事件，
     * 待从库看见同事务 Outbox 后再删除永久缓存，确保所有消费服务读取同一份币种资料。
     */
    private final IsoDictionaryCacheInvalidator isoDictionaryCacheInvalidator;

    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建币种基础资料应用服务。
     *
     * @param isoCurrencyMapper 币种 Mapper
     * @param isoCurrencyConverter 币种接口模型转换器
     * @param isoDictionaryCacheInvalidator ISO 字典缓存失效器
     * @param excelExportService Excel 导出服务
     * @param excelI18nMessageResolver Excel 国际化文案解析器
     * @param excelLocaleResolver Excel 语言解析器
     */
    public AdminBaseCurrencyApplicationService(IsoCurrencyMapper isoCurrencyMapper,
                                               IsoCurrencyConverter isoCurrencyConverter,
                                               IsoDictionaryCacheInvalidator isoDictionaryCacheInvalidator,
                                               ExcelExportService excelExportService,
                                               ExcelI18nMessageResolver excelI18nMessageResolver,
                                               ExcelLocaleResolver excelLocaleResolver) {
        this.isoCurrencyMapper = isoCurrencyMapper;
        this.isoCurrencyConverter = isoCurrencyConverter;
        this.isoDictionaryCacheInvalidator = isoDictionaryCacheInvalidator;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 分页查询币种基础资料。
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @param keyword  关键字
     * @param status   状态
     * @return 分页结果
     */
    @DS(DataSourceName.SLAVE)
    public PageResult<Response> pageCurrencies(int pageNo, int pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<IsoCurrencyDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(IsoCurrencyDO::getAlpha3Code, keyword.trim())
                    .or().like(IsoCurrencyDO::getNumericCode, keyword.trim())
                    .or().like(IsoCurrencyDO::getEnglishName, keyword.trim())
                    .or().like(IsoCurrencyDO::getChineseName, keyword.trim()));
        }
        queryWrapper.eq(status != null, IsoCurrencyDO::getStatus, status);
        queryWrapper.eq(IsoCurrencyDO::getDeleted, AuthConstants.NOT_DELETED);
        queryWrapper.orderByAsc(IsoCurrencyDO::getAlpha3Code);

        Page<IsoCurrencyDO> page = isoCurrencyMapper.selectPage(new Page<>(pageNo, pageSize), queryWrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                isoCurrencyConverter.toResponses(page.getRecords()));
    }

    /**
     * 查询单个币种详情。
     *
     * @param id 主键
     * @return 币种详情
     */
    @DS(DataSourceName.SLAVE)
    public Response getCurrency(Long id) {
        return isoCurrencyConverter.toResponse(isoCurrencyMapper.selectById(id));
    }

    /**
     * 导出全部币种资料。
     *
     * @return 币种列表
     */
    @DS(DataSourceName.SLAVE)
    public void exportCurrencies(String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<IsoCurrencyExportRow> rows = isoCurrencyMapper.selectList(new LambdaQueryWrapper<IsoCurrencyDO>()
                        .eq(IsoCurrencyDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(IsoCurrencyDO::getAlpha3Code))
                .stream()
                .map(currency -> toExportRow(currency, locale))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<IsoCurrencyExportRow>builder()
                        .fileName(excelI18nMessageResolver.resolve("excel.currency.title", locale) + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName(excelI18nMessageResolver.resolve("excel.currency.title", locale))
                        .titleKey("excel.currency.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(excelI18nMessageResolver.resolve("excel.common.noCondition", locale))
                        .rowClass(IsoCurrencyExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 新增币种资料。
     *
     * @param request 币种保存请求
     * @return 保存后的币种资料
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Response createCurrency(SaveRequest request) {
        IsoCurrencyDO currency = isoCurrencyConverter.toEntity(request);
        currency.setId(null);
        currency.setIconKey(IsoCurrencyIconKey.normalize(currency.getAlpha3Code(), currency.getIconKey()));
        currency.setCreatedAt(LocalDateTime.now());
        currency.setUpdatedAt(LocalDateTime.now());
        currency.setDeleted(NOT_DELETED);
        if (currency.getStatus() == null) {
            currency.setStatus(DEFAULT_ENABLED_STATUS);
        }
        isoCurrencyMapper.insert(currency);
        isoDictionaryCacheInvalidator.evictCurrencies();
        return isoCurrencyConverter.toResponse(currency);
    }

    /**
     * 更新币种资料。
     *
     * @param id    主键
     * @param request 更新输入
     * @return 更新结果
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Response> updateCurrency(Long id, SaveRequest request) {
        IsoCurrencyDO currency = isoCurrencyMapper.selectById(id);
        if (currency == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "currency not found");
        }
        mergeCurrency(currency, request);
        currency.setUpdatedAt(LocalDateTime.now());
        isoCurrencyMapper.updateById(currency);
        isoDictionaryCacheInvalidator.evictCurrencies();
        return success(isoCurrencyConverter.toResponse(currency));
    }

    /**
     * 更新币种状态。
     *
     * @param id   主键
     * @param request 状态更新请求
     * @return 更新结果
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Response> updateStatus(Long id, IsoStatusUpdateRequest request) {
        IsoCurrencyDO currency = isoCurrencyMapper.selectById(id);
        if (currency == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "currency not found");
        }
        currency.setStatus(request.getStatus());
        currency.setUpdatedAt(LocalDateTime.now());
        isoCurrencyMapper.updateById(currency);
        isoDictionaryCacheInvalidator.evictCurrencies();
        return success(isoCurrencyConverter.toResponse(currency));
    }

    /**
     * 逻辑删除币种资料。
     *
     * @param id 主键
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void removeCurrency(Long id) {
        IsoCurrencyDO currency = isoCurrencyMapper.selectById(id);
        if (currency != null) {
            currency.setDeleted(DELETED);
            currency.setUpdatedAt(LocalDateTime.now());
            isoCurrencyMapper.updateById(currency);
            isoDictionaryCacheInvalidator.evictCurrencies();
        }
    }

    /**
     * 用非空字段合并币种更新内容。
     *
     * @param currency 当前持久化实体
     * @param request  本次更新输入
     */
    private void mergeCurrency(IsoCurrencyDO currency, SaveRequest request) {
        boolean alpha3CodeChanged = false;
        if (request.getAlpha3Code() != null) {
            alpha3CodeChanged = !request.getAlpha3Code().equalsIgnoreCase(currency.getAlpha3Code());
            currency.setAlpha3Code(request.getAlpha3Code());
        }
        if (request.getNumericCode() != null) {
            currency.setNumericCode(request.getNumericCode());
        }
        if (request.getEnglishName() != null) {
            currency.setEnglishName(request.getEnglishName());
        }
        if (request.getChineseName() != null) {
            currency.setChineseName(request.getChineseName());
        }
        if (request.getCurrencySymbol() != null) {
            currency.setCurrencySymbol(request.getCurrencySymbol());
        }
        if (request.getIconKey() != null) {
            currency.setIconKey(IsoCurrencyIconKey.normalize(
                    request.getAlpha3Code() == null ? currency.getAlpha3Code() : request.getAlpha3Code(),
                    request.getIconKey()
            ));
        } else if (alpha3CodeChanged) {
            currency.setIconKey(null);
        }
        if (request.getFractionDigits() != null) {
            currency.setFractionDigits(request.getFractionDigits());
        }
        if (request.getMinorUnitMultiplier() != null) {
            currency.setMinorUnitMultiplier(request.getMinorUnitMultiplier());
        }
        if (request.getMinimumAmount() != null) {
            currency.setMinimumAmount(request.getMinimumAmount());
        }
        if (request.getStatus() != null) {
            currency.setStatus(request.getStatus());
        }
    }

    /**
     * 将币种实体转换为导出行对象。
     *
     * @param currency 币种实体
     * @param locale 当前语言
     * @return 导出行对象
     */
    private IsoCurrencyExportRow toExportRow(IsoCurrencyDO currency, Locale locale) {
        IsoCurrencyExportRow row = new IsoCurrencyExportRow();
        row.setAlpha3Code(currency.getAlpha3Code());
        row.setNumericCode(currency.getNumericCode());
        row.setChineseName(currency.getChineseName());
        row.setEnglishName(currency.getEnglishName());
        row.setCurrencySymbol(currency.getCurrencySymbol());
        row.setFractionDigits(currency.getFractionDigits());
        row.setStatus(excelI18nMessageResolver.resolve(
                currency.getStatus() != null && currency.getStatus() == 1 ? "excel.common.enabled" : "excel.common.disabled",
                locale
        ));
        row.setCreatedAt(currency.getCreatedAt());
        return row;
    }
}
