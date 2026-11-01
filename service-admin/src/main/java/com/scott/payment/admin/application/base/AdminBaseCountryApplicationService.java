package com.scott.payment.admin.application.base;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.converter.IsoCountryConverter;
import com.scott.payment.admin.dto.base.IsoCountryDTOs.Response;
import com.scott.payment.admin.dto.base.IsoCountryDTOs.SaveRequest;
import com.scott.payment.admin.dto.export.IsoCountryExportRow;
import com.scott.payment.admin.dto.base.IsoStatusUpdateRequest;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
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
 * 国家地区基础资料应用服务
 *
 * <p>负责管理后台国家地区基础资料用例编排，包括分页查询、详情查询、新增、更新、状态切换和逻辑删除。</p>
 */
@Service
public class AdminBaseCountryApplicationService {

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
     * 国家地区数据访问组件。
     */
    private final IsoCountryMapper isoCountryMapper;

    /**
     * 国家和地区接口模型转换器。
     */
    private final IsoCountryConverter isoCountryConverter;

    /**
     * ISO 字典缓存失效器。写操作在主库事务内登记 {@code all} 与 {@code codes} 两个失效事件，
     * 待从库看见同事务 Outbox 后再删除永久缓存，供管理端、OpenAPI、支付和收银台按需重建。
     */
    private final IsoDictionaryCacheInvalidator isoDictionaryCacheInvalidator;

    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建国家地区基础资料应用服务。
     *
     * @param isoCountryMapper 国家地区 Mapper
     * @param isoCountryConverter 国家地区接口模型转换器
     * @param isoDictionaryCacheInvalidator ISO 字典缓存失效器
     * @param excelExportService Excel 导出服务
     * @param excelI18nMessageResolver Excel 国际化文案解析器
     * @param excelLocaleResolver Excel 语言解析器
     */
    public AdminBaseCountryApplicationService(IsoCountryMapper isoCountryMapper,
                                              IsoCountryConverter isoCountryConverter,
                                              IsoDictionaryCacheInvalidator isoDictionaryCacheInvalidator,
                                              ExcelExportService excelExportService,
                                              ExcelI18nMessageResolver excelI18nMessageResolver,
                                              ExcelLocaleResolver excelLocaleResolver) {
        this.isoCountryMapper = isoCountryMapper;
        this.isoCountryConverter = isoCountryConverter;
        this.isoDictionaryCacheInvalidator = isoDictionaryCacheInvalidator;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 分页查询国家地区基础资料。
     *
     * @param pageNo        页码
     * @param pageSize      每页大小
     * @param keyword       关键字
     * @param continentCode 大洲编码
     * @param status        状态
     * @return 分页结果
     */
    @DS(DataSourceName.SLAVE)
    public PageResult<Response> pageCountries(int pageNo, int pageSize, String keyword,
                                              String continentCode, Integer status) {
        LambdaQueryWrapper<IsoCountryDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(IsoCountryDO::getAlpha2Code, keyword.trim())
                    .or().like(IsoCountryDO::getAlpha3Code, keyword.trim())
                    .or().like(IsoCountryDO::getEnglishName, keyword.trim())
                    .or().like(IsoCountryDO::getChineseName, keyword.trim()));
        }
        queryWrapper.eq(StringUtils.hasText(continentCode), IsoCountryDO::getContinentCode, continentCode);
        queryWrapper.eq(status != null, IsoCountryDO::getStatus, status);
        queryWrapper.eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED);
        queryWrapper.orderByAsc(IsoCountryDO::getAlpha2Code);

        Page<IsoCountryDO> page = isoCountryMapper.selectPage(new Page<>(pageNo, pageSize), queryWrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                isoCountryConverter.toResponses(page.getRecords()));
    }

    /**
     * 查询单个国家地区详情。
     *
     * @param id 主键
     * @return 国家地区详情
     */
    @DS(DataSourceName.SLAVE)
    public Response getCountry(Long id) {
        return isoCountryConverter.toResponse(isoCountryMapper.selectById(id));
    }

    /**
     * 导出全部国家地区资料。
     *
     * @return 国家地区列表
     */
    @DS(DataSourceName.SLAVE)
    public void exportCountries(String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<IsoCountryExportRow> rows = isoCountryMapper.selectList(new LambdaQueryWrapper<IsoCountryDO>()
                        .eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(IsoCountryDO::getAlpha2Code))
                .stream()
                .map(country -> toExportRow(country, locale))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<IsoCountryExportRow>builder()
                        .fileName(excelI18nMessageResolver.resolve("excel.country.title", locale) + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName(excelI18nMessageResolver.resolve("excel.country.title", locale))
                        .titleKey("excel.country.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(excelI18nMessageResolver.resolve("excel.common.noCondition", locale))
                        .rowClass(IsoCountryExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 新增国家地区资料。
     *
     * @param request 国家地区保存请求
     * @return 保存后的国家地区资料
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Response createCountry(SaveRequest request) {
        IsoCountryDO country = isoCountryConverter.toEntity(request);
        country.setId(null);
        country.setCreatedAt(LocalDateTime.now());
        country.setUpdatedAt(LocalDateTime.now());
        country.setDeleted(NOT_DELETED);
        if (country.getStatus() == null) {
            country.setStatus(DEFAULT_ENABLED_STATUS);
        }
        isoCountryMapper.insert(country);
        isoDictionaryCacheInvalidator.evictCountries();
        return isoCountryConverter.toResponse(country);
    }

    /**
     * 更新国家地区资料。
     *
     * @param id    主键
     * @param request 更新输入
     * @return 更新结果
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Response> updateCountry(Long id, SaveRequest request) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        mergeCountry(country, request);
        country.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(country);
        isoDictionaryCacheInvalidator.evictCountries();
        return success(isoCountryConverter.toResponse(country));
    }

    /**
     * 更新国家地区状态。
     *
     * @param id   主键
     * @param request 状态更新请求
     * @return 更新结果
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Response> updateStatus(Long id, IsoStatusUpdateRequest request) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        country.setStatus(request.getStatus());
        country.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(country);
        isoDictionaryCacheInvalidator.evictCountries();
        return success(isoCountryConverter.toResponse(country));
    }

    /**
     * 逻辑删除国家地区资料。
     *
     * @param id 主键
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void removeCountry(Long id) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country != null) {
            country.setDeleted(DELETED);
            country.setUpdatedAt(LocalDateTime.now());
            isoCountryMapper.updateById(country);
            isoDictionaryCacheInvalidator.evictCountries();
        }
    }

    /**
     * 用非空字段合并更新内容，避免覆盖前端未提交的字段。
     *
     * @param country 当前持久化实体
     * @param request 本次更新输入
     */
    private void mergeCountry(IsoCountryDO country, SaveRequest request) {
        if (request.getContinentCode() != null) {
            country.setContinentCode(request.getContinentCode());
        }
        if (request.getContinentName() != null) {
            country.setContinentName(request.getContinentName());
        }
        if (request.getAlpha2Code() != null) {
            country.setAlpha2Code(request.getAlpha2Code());
        }
        if (request.getAlpha3Code() != null) {
            country.setAlpha3Code(request.getAlpha3Code());
        }
        if (request.getNumericCode() != null) {
            country.setNumericCode(request.getNumericCode());
        }
        if (request.getEnglishName() != null) {
            country.setEnglishName(request.getEnglishName());
        }
        if (request.getShortEnglishName() != null) {
            country.setShortEnglishName(request.getShortEnglishName());
        }
        if (request.getChineseName() != null) {
            country.setChineseName(request.getChineseName());
        }
        if (request.getFlagEmoji() != null) {
            country.setFlagEmoji(request.getFlagEmoji());
        }
        if (request.getPrimaryLanguageCode() != null) {
            country.setPrimaryLanguageCode(request.getPrimaryLanguageCode());
        }
        if (request.getPrimaryLanguageEnglish() != null) {
            country.setPrimaryLanguageEnglish(request.getPrimaryLanguageEnglish());
        }
        if (request.getPrimaryLanguageChinese() != null) {
            country.setPrimaryLanguageChinese(request.getPrimaryLanguageChinese());
        }
        if (request.getCurrencyAlpha3Code() != null) {
            country.setCurrencyAlpha3Code(request.getCurrencyAlpha3Code());
        }
        if (request.getStatus() != null) {
            country.setStatus(request.getStatus());
        }
    }

    /**
     * 将国家地区实体转换为导出行对象。
     *
     * @param country 国家地区实体
     * @param locale 当前语言
     * @return 导出行对象
     */
    private IsoCountryExportRow toExportRow(IsoCountryDO country, Locale locale) {
        IsoCountryExportRow row = new IsoCountryExportRow();
        row.setAlpha2Code(country.getAlpha2Code());
        row.setAlpha3Code(country.getAlpha3Code());
        row.setChineseName(country.getChineseName());
        row.setEnglishName(country.getEnglishName());
        row.setContinentName(country.getContinentName());
        row.setCurrencyAlpha3Code(country.getCurrencyAlpha3Code());
        row.setStatus(excelI18nMessageResolver.resolve(
                country.getStatus() != null && country.getStatus() == 1 ? "excel.common.enabled" : "excel.common.disabled",
                locale
        ));
        row.setCreatedAt(country.getCreatedAt());
        return row;
    }
}
