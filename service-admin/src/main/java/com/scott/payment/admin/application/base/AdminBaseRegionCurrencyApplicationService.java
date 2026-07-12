package com.scott.payment.admin.application.base;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.export.RegionCurrencyExportRow;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseRegionCurrencyApplicationService
 * @date : 2026-06-19 21:10
 * @email : scott_x@163.com
 * @description : 地区币种配置应用服务
 * @status : create
 *
 * <p>负责管理后台国家地区与默认币种映射关系的用例编排，避免控制器直接拼装 Mapper 查询结果，
 * 并统一处理分页展示、详情查询、导出、绑定更新与状态切换。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseRegionCurrencyApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Admin Base Region Currency Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminBaseRegionCurrencyApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 币种启用状态。
     */
    private static final int ENABLED_STATUS = 1;

    /**
     * 国家地区 Mapper。
     */
    private final IsoCountryMapper isoCountryMapper;

    /**
     * 币种 Mapper。
     */
    private final IsoCurrencyMapper isoCurrencyMapper;
    /**
     * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final ExcelExportService excelExportService;
    /**
     * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    /**
     * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建地区币种配置应用服务。
     *
     * @param isoCountryMapper  国家地区 Mapper
     * @param isoCurrencyMapper 币种 Mapper
     * @param excelExportService Excel 导出服务
     */
    public AdminBaseRegionCurrencyApplicationService(IsoCountryMapper isoCountryMapper,
                                                     IsoCurrencyMapper isoCurrencyMapper,
                                                     ExcelExportService excelExportService,
                                                     ExcelI18nMessageResolver excelI18nMessageResolver,
                                                     ExcelLocaleResolver excelLocaleResolver) {
        this.isoCountryMapper = isoCountryMapper;
        this.isoCurrencyMapper = isoCurrencyMapper;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 分页查询地区与币种映射。
     *
     * @param pageNo        页码
     * @param pageSize      每页大小
     * @param keyword       关键字
     * @param continentCode 大洲编码
     * @return 分页结果
     */
    /**
     * 查询基础数据列表或分页数据，供页面筛选和展示使用。
     * @param pageNo 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param pageSize 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param keyword 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param continentCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public PageResult<Map<String, Object>> pageRegionCurrencies(int pageNo, int pageSize,
                                                                String keyword, String continentCode) {
        Map<String, IsoCurrencyDO> currencyMap = enabledCurrencyMap();

        LambdaQueryWrapper<IsoCountryDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(IsoCountryDO::getAlpha2Code, keyword.trim())
                    .or().like(IsoCountryDO::getAlpha3Code, keyword.trim())
                    .or().like(IsoCountryDO::getEnglishName, keyword.trim())
                    .or().like(IsoCountryDO::getChineseName, keyword.trim()));
        }
        queryWrapper.eq(StringUtils.hasText(continentCode), IsoCountryDO::getContinentCode, continentCode);
        queryWrapper.eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED);
        queryWrapper.orderByAsc(IsoCountryDO::getAlpha2Code);

        Page<IsoCountryDO> page = isoCountryMapper.selectPage(new Page<>(pageNo, pageSize), queryWrapper);
        List<Map<String, Object>> records = page.getRecords().stream()
                .map(country -> toRegionCurrencyRow(country, currencyMap))
                .toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 查询单条地区与币种映射。
     *
     * @param id 国家地区主键
     * @return 映射详情
     */
    /**
     * 获取基础数据明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<Map<String, Object>> getRegionCurrency(Long id) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        return success(toRegionCurrencyRow(country, currencyMap()));
    }

    /**
     * 导出地区与币种映射。
     *
     * @return 全量映射列表
     */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param operator 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param response 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void exportRegionCurrencies(String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        Map<String, IsoCurrencyDO> currencyMap = currencyMap();
        List<RegionCurrencyExportRow> rows = isoCountryMapper.selectList(new LambdaQueryWrapper<IsoCountryDO>()
                        .eq(IsoCountryDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(IsoCountryDO::getAlpha2Code))
                .stream()
                .map(country -> toRegionCurrencyExportRow(country, currencyMap, locale))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<RegionCurrencyExportRow>builder()
                        .fileName(excelI18nMessageResolver.resolve("excel.regionCurrency.title", locale) + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName(excelI18nMessageResolver.resolve("excel.regionCurrency.title", locale))
                        .titleKey("excel.regionCurrency.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(excelI18nMessageResolver.resolve("excel.common.noCondition", locale))
                        .rowClass(RegionCurrencyExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 新增地区币种映射。
     *
     * @param body 请求体
     * @return 处理结果
     */
    /**
     * 创建或保存基础数据数据，保持请求校验、默认值和审计字段一致。
     * @param Map<String 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param body 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<Void> createRegionCurrency(Map<String, String> body) {
        return updateCountryCurrency(Long.valueOf(body.get("countryId")), body.get("currencyAlpha3Code"));
    }

    /**
     * 更新地区币种映射。
     *
     * @param id   国家地区主键
     * @param body 请求体
     * @return 处理结果
     */
    /**
     * 更新基础数据数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param Map<String 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param body 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<Void> updateRegionCurrency(Long id, Map<String, String> body) {
        return updateCountryCurrency(id, body.get("currencyAlpha3Code"));
    }

    /**
     * 删除地区币种映射。
     *
     * @param id 国家地区主键
     * @return 处理结果
     */
    /**
     * 删除基础数据数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<Void> removeRegionCurrency(Long id) {
        return updateCountryCurrency(id, "");
    }

    /**
     * 更新国家地区状态。
     *
     * @param id   国家地区主键
     * @param body 状态请求体
     * @return 处理结果
     */
    /**
     * 更新基础数据数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param Map<String 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param body 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<Void> updateStatus(Long id, Map<String, Integer> body) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        country.setStatus(body.get("status"));
        country.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(country);
        return success();
    }

    /**
     * 更新国家地区默认币种。
     *
     * @param id           国家地区主键
     * @param currencyCode 币种编码
     * @return 处理结果
     */
    private CommonResult<Void> updateCountryCurrency(Long id, String currencyCode) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        country.setCurrencyAlpha3Code(StringUtils.hasText(currencyCode) ? currencyCode.trim() : "");
        country.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(country);
        return success();
    }

    /**
     * 查询启用状态的币种映射，供分页列表展示。
     *
     * @return 币种映射
     */
    private Map<String, IsoCurrencyDO> enabledCurrencyMap() {
        Map<String, IsoCurrencyDO> currencyMap = new LinkedHashMap<>();
        isoCurrencyMapper.selectList(new LambdaQueryWrapper<IsoCurrencyDO>()
                        .eq(IsoCurrencyDO::getDeleted, AuthConstants.NOT_DELETED)
                        .eq(IsoCurrencyDO::getStatus, ENABLED_STATUS))
                .forEach(currency -> currencyMap.put(currency.getAlpha3Code(), currency));
        return currencyMap;
    }

    /**
     * 查询全部币种映射，供详情和导出使用。
     *
     * @return 币种映射
     */
    private Map<String, IsoCurrencyDO> currencyMap() {
        Map<String, IsoCurrencyDO> currencyMap = new LinkedHashMap<>();
        isoCurrencyMapper.selectList(new LambdaQueryWrapper<IsoCurrencyDO>()
                        .eq(IsoCurrencyDO::getDeleted, AuthConstants.NOT_DELETED))
                .forEach(currency -> currencyMap.put(currency.getAlpha3Code(), currency));
        return currencyMap;
    }

    /**
     * 组装地区币种行数据。
     *
     * @param country     国家地区
     * @param currencyMap 币种映射
     * @return 行数据
     */
    private Map<String, Object> toRegionCurrencyRow(IsoCountryDO country, Map<String, IsoCurrencyDO> currencyMap) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", country.getId());
        row.put("alpha2Code", country.getAlpha2Code());
        row.put("alpha3Code", country.getAlpha3Code());
        row.put("countryName", country.getChineseName());
        row.put("countryEnglishName", country.getEnglishName());
        row.put("continentCode", country.getContinentCode());
        row.put("continentName", country.getContinentName());
        row.put("currencyAlpha3Code", country.getCurrencyAlpha3Code());
        IsoCurrencyDO currency = currencyMap.get(country.getCurrencyAlpha3Code());
        row.put("currencyName", currency != null ? currency.getChineseName() : "-");
        row.put("currencySymbol", currency != null ? currency.getCurrencySymbol() : "");
        row.put("status", country.getStatus());
        return row;
    }

    /**
     * 将地区币种映射转换为导出行对象，避免前端再自行拼装展示值。
     *
     * @param country 国家地区实体
     * @param currencyMap 币种映射
     * @param locale 当前语言
     * @return 导出行对象
     */
    private RegionCurrencyExportRow toRegionCurrencyExportRow(IsoCountryDO country,
                                                              Map<String, IsoCurrencyDO> currencyMap,
                                                              Locale locale) {
        RegionCurrencyExportRow row = new RegionCurrencyExportRow();
        IsoCurrencyDO currency = currencyMap.get(country.getCurrencyAlpha3Code());
        row.setAlpha2Code(country.getAlpha2Code());
        row.setCountryName(country.getChineseName());
        row.setContinentName(country.getContinentName());
        row.setCurrencyAlpha3Code(country.getCurrencyAlpha3Code());
        row.setCurrencyName(currency != null ? currency.getChineseName() : "-");
        row.setCurrencySymbol(currency != null ? currency.getCurrencySymbol() : "");
        row.setStatus(excelI18nMessageResolver.resolve(
                country.getStatus() != null && country.getStatus() == 1 ? "excel.common.enabled" : "excel.common.disabled",
                locale
        ));
        return row;
    }
}
