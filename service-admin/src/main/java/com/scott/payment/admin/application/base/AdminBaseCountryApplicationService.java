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
import com.scott.payment.component.db.iso.service.IsoDictionaryCacheInvalidator;
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
     * ISO 字典缓存失效器；国家资料持久化成功后同步删除新旧国家缓存。
     */
    private final IsoDictionaryCacheInvalidator isoDictionaryCacheInvalidator;

    /**
     * excel Export Service 依赖，用于 Admin Base Country Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelExportService excelExportService;
    /**
     * excel I 18 n Message Resolver，用于保存 Admin Base Country Application Service 中与 exceli18nmessageresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    /**
     * excel Locale Resolver，用于保存 Admin Base Country Application Service 中与 excellocaleresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建国家地区基础资料应用服务。
     *
     * @param isoCountryMapper 国家地区 Mapper
     * @param isoDictionaryCacheInvalidator ISO 字典缓存失效器
     * @param excelExportService Excel 导出服务
     */
    public AdminBaseCountryApplicationService(IsoCountryMapper isoCountryMapper,
                                              IsoDictionaryCacheInvalidator isoDictionaryCacheInvalidator,
                                              ExcelExportService excelExportService,
                                              ExcelI18nMessageResolver excelI18nMessageResolver,
                                              ExcelLocaleResolver excelLocaleResolver) {
        this.isoCountryMapper = isoCountryMapper;
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
    public IsoCountryDO getCountry(Long id) {
        return isoCountryMapper.selectById(id);
    }

    /**
     * 导出全部国家地区资料。
     *
     * @return 国家地区列表
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
    public IsoCountryDO createCountry(IsoCountryDO country) {
        country.setId(null);
        country.setCreatedAt(LocalDateTime.now());
        country.setUpdatedAt(LocalDateTime.now());
        country.setDeleted(NOT_DELETED);
        if (country.getStatus() == null) {
            country.setStatus(DEFAULT_ENABLED_STATUS);
        }
        isoCountryMapper.insert(country);
        isoDictionaryCacheInvalidator.evictCountries();
        return country;
    }

    /**
     * 更新国家地区资料。
     *
     * @param id    主键
     * @param input 更新输入
     * @return 更新结果
     */
    public CommonResult<IsoCountryDO> updateCountry(Long id, IsoCountryDO input) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        mergeCountry(country, input);
        country.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(country);
        isoDictionaryCacheInvalidator.evictCountries();
        return success(country);
    }

    /**
     * 更新国家地区状态。
     *
     * @param id   主键
     * @param body 状态请求体
     * @return 更新结果
     */
    public CommonResult<IsoCountryDO> updateStatus(Long id, Map<String, Integer> body) {
        IsoCountryDO country = isoCountryMapper.selectById(id);
        if (country == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "country not found");
        }
        country.setStatus(body.get("status"));
        country.setUpdatedAt(LocalDateTime.now());
        isoCountryMapper.updateById(country);
        isoDictionaryCacheInvalidator.evictCountries();
        return success(country);
    }

    /**
     * 逻辑删除国家地区资料。
     *
     * @param id 主键
     */
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
