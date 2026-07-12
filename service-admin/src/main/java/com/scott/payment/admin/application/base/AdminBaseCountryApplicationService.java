package com.scott.payment.admin.application.base;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.export.IsoCountryExportRow;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseCountryApplicationService
 * @date : 2026-06-19 21:08
 * @email : scott_x@163.com
 * @description : 国家地区基础资料应用服务
 * @status : create
 *
 * <p>负责管理后台国家地区基础资料用例编排，包括分页查询、详情查询、新增、更新、状态切换和逻辑删除。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseCountryApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Admin Base Country Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
     * 创建国家地区基础资料应用服务。
     *
     * @param isoCountryMapper 国家地区 Mapper
     * @param excelExportService Excel 导出服务
     */
    public AdminBaseCountryApplicationService(IsoCountryMapper isoCountryMapper,
                                              ExcelExportService excelExportService,
                                              ExcelI18nMessageResolver excelI18nMessageResolver,
                                              ExcelLocaleResolver excelLocaleResolver) {
        this.isoCountryMapper = isoCountryMapper;
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
    /**
     * 查询基础数据列表或分页数据，供页面筛选和展示使用。
     * @param pageNo 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param pageSize 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param keyword 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param continentCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public PageResult<IsoCountryDO> pageCountries(int pageNo, int pageSize, String keyword,
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
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 查询单个国家地区详情。
     *
     * @param id 主键
     * @return 国家地区详情
     */
    /**
     * 获取基础数据明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public IsoCountryDO getCountry(Long id) {
        return isoCountryMapper.selectById(id);
    }

    /**
     * 导出全部国家地区资料。
     *
     * @return 国家地区列表
     */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param operator 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param response 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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
     * @param country 国家地区实体
     * @return 保存后的实体
     */
    /**
     * 创建或保存基础数据数据，保持请求校验、默认值和审计字段一致。
     * @param country 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public IsoCountryDO createCountry(IsoCountryDO country) {
        country.setId(null);
        country.setCreatedAt(LocalDateTime.now());
        country.setUpdatedAt(LocalDateTime.now());
        country.setDeleted(NOT_DELETED);
        if (country.getStatus() == null) {
            country.setStatus(DEFAULT_ENABLED_STATUS);
        }
        isoCountryMapper.insert(country);
        return country;
    }

    /**
     * 更新国家地区资料。
     *
     * @param id    主键
     * @param input 更新输入
     * @return 更新结果
     */
    /**
     * 更新基础数据数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param input 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<IsoCountryDO> updateCountry(Long id, IsoCountryDO input) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        mergeCountry(country, input);
        country.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(country);
        return success(country);
    }

    /**
     * 更新国家地区状态。
     *
     * @param id   主键
     * @param body 状态请求体
     * @return 更新结果
     */
    /**
     * 更新基础数据数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param Map<String 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param body 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<IsoCountryDO> updateStatus(Long id, Map<String, Integer> body) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        country.setStatus(body.get("status"));
        country.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(country);
        return success(country);
    }

    /**
     * 逻辑删除国家地区资料。
     *
     * @param id 主键
     */
    /**
     * 删除基础数据数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void removeCountry(Long id) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country != null) {
            country.setDeleted(DELETED);
            country.setUpdatedAt(LocalDateTime.now());
            isoCountryMapper.updateById(country);
        }
    }

    /**
     * 用非空字段合并更新内容，避免覆盖前端未提交的字段。
     *
     * @param country 当前持久化实体
     * @param input   本次更新输入
     */
    private void mergeCountry(IsoCountryDO country, IsoCountryDO input) {
        if (input.getContinentCode() != null) {
            country.setContinentCode(input.getContinentCode());
        }
        if (input.getContinentName() != null) {
            country.setContinentName(input.getContinentName());
        }
        if (input.getAlpha2Code() != null) {
            country.setAlpha2Code(input.getAlpha2Code());
        }
        if (input.getAlpha3Code() != null) {
            country.setAlpha3Code(input.getAlpha3Code());
        }
        if (input.getNumericCode() != null) {
            country.setNumericCode(input.getNumericCode());
        }
        if (input.getEnglishName() != null) {
            country.setEnglishName(input.getEnglishName());
        }
        if (input.getShortEnglishName() != null) {
            country.setShortEnglishName(input.getShortEnglishName());
        }
        if (input.getChineseName() != null) {
            country.setChineseName(input.getChineseName());
        }
        if (input.getFlagEmoji() != null) {
            country.setFlagEmoji(input.getFlagEmoji());
        }
        if (input.getPrimaryLanguageCode() != null) {
            country.setPrimaryLanguageCode(input.getPrimaryLanguageCode());
        }
        if (input.getPrimaryLanguageEnglish() != null) {
            country.setPrimaryLanguageEnglish(input.getPrimaryLanguageEnglish());
        }
        if (input.getPrimaryLanguageChinese() != null) {
            country.setPrimaryLanguageChinese(input.getPrimaryLanguageChinese());
        }
        if (input.getCurrencyAlpha3Code() != null) {
            country.setCurrencyAlpha3Code(input.getCurrencyAlpha3Code());
        }
        if (input.getStatus() != null) {
            country.setStatus(input.getStatus());
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
