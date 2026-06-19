package com.scott.payment.admin.application.base;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.export.IsoCurrencyExportRow;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
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
 * @classname : AdminBaseCurrencyApplicationService
 * @date : 2026-06-19 21:09
 * @email : scott_x@163.com
 * @description : 币种基础资料应用服务
 * @status : create
 *
 * <p>负责管理后台币种基础资料用例编排，包括分页查询、详情查询、新增、更新、状态切换和逻辑删除。</p>
 */
@Service
public class AdminBaseCurrencyApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

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
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建币种基础资料应用服务。
     *
     * @param isoCurrencyMapper 币种 Mapper
     * @param excelExportService Excel 导出服务
     */
    public AdminBaseCurrencyApplicationService(IsoCurrencyMapper isoCurrencyMapper,
                                               ExcelExportService excelExportService,
                                               ExcelI18nMessageResolver excelI18nMessageResolver,
                                               ExcelLocaleResolver excelLocaleResolver) {
        this.isoCurrencyMapper = isoCurrencyMapper;
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
    public PageResult<IsoCurrencyDO> pageCurrencies(int pageNo, int pageSize, String keyword, Integer status) {
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
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 查询单个币种详情。
     *
     * @param id 主键
     * @return 币种详情
     */
    public IsoCurrencyDO getCurrency(Long id) {
        return isoCurrencyMapper.selectById(id);
    }

    /**
     * 导出全部币种资料。
     *
     * @return 币种列表
     */
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
                        .fileName("币种列表_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName("币种列表")
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
     * @param currency 币种实体
     * @return 保存后的实体
     */
    public IsoCurrencyDO createCurrency(IsoCurrencyDO currency) {
        currency.setId(null);
        currency.setCreatedAt(LocalDateTime.now());
        currency.setUpdatedAt(LocalDateTime.now());
        currency.setDeleted(NOT_DELETED);
        if (currency.getStatus() == null) {
            currency.setStatus(DEFAULT_ENABLED_STATUS);
        }
        isoCurrencyMapper.insert(currency);
        return currency;
    }

    /**
     * 更新币种资料。
     *
     * @param id    主键
     * @param input 更新输入
     * @return 更新结果
     */
    public CommonResult<IsoCurrencyDO> updateCurrency(Long id, IsoCurrencyDO input) {
        IsoCurrencyDO currency = isoCurrencyMapper.selectById(id);
        if (currency == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "currency not found");
        }
        mergeCurrency(currency, input);
        currency.setUpdatedAt(LocalDateTime.now());
        isoCurrencyMapper.updateById(currency);
        return success(currency);
    }

    /**
     * 更新币种状态。
     *
     * @param id   主键
     * @param body 状态请求体
     * @return 更新结果
     */
    public CommonResult<IsoCurrencyDO> updateStatus(Long id, Map<String, Integer> body) {
        IsoCurrencyDO currency = isoCurrencyMapper.selectById(id);
        if (currency == null) {
            return CommonResult.error(ApiResultEnum.NOT_FOUND.getCode(), "currency not found");
        }
        currency.setStatus(body.get("status"));
        currency.setUpdatedAt(LocalDateTime.now());
        isoCurrencyMapper.updateById(currency);
        return success(currency);
    }

    /**
     * 逻辑删除币种资料。
     *
     * @param id 主键
     */
    public void removeCurrency(Long id) {
        IsoCurrencyDO currency = isoCurrencyMapper.selectById(id);
        if (currency != null) {
            currency.setDeleted(DELETED);
            currency.setUpdatedAt(LocalDateTime.now());
            isoCurrencyMapper.updateById(currency);
        }
    }

    /**
     * 用非空字段合并币种更新内容。
     *
     * @param currency 当前持久化实体
     * @param input    本次更新输入
     */
    private void mergeCurrency(IsoCurrencyDO currency, IsoCurrencyDO input) {
        if (input.getAlpha3Code() != null) {
            currency.setAlpha3Code(input.getAlpha3Code());
        }
        if (input.getNumericCode() != null) {
            currency.setNumericCode(input.getNumericCode());
        }
        if (input.getEnglishName() != null) {
            currency.setEnglishName(input.getEnglishName());
        }
        if (input.getChineseName() != null) {
            currency.setChineseName(input.getChineseName());
        }
        if (input.getCurrencySymbol() != null) {
            currency.setCurrencySymbol(input.getCurrencySymbol());
        }
        if (input.getFractionDigits() != null) {
            currency.setFractionDigits(input.getFractionDigits());
        }
        if (input.getMinorUnitMultiplier() != null) {
            currency.setMinorUnitMultiplier(input.getMinorUnitMultiplier());
        }
        if (input.getMinimumAmount() != null) {
            currency.setMinimumAmount(input.getMinimumAmount());
        }
        if (input.getStatus() != null) {
            currency.setStatus(input.getStatus());
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
