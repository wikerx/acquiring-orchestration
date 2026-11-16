package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.MerchantExportScopeSnapshot;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 商户结算异步导出的共享边界校验和本地化支持，不参与任何金额或汇率计算。
 */
final class MerchantSettlementExportSupport {

    /**
     * 工具类不允许实例化。
     */
    private MerchantSettlementExportSupport() {
    }

    /**
     * 交叉校验任务归属和冻结范围，防止损坏快照跨商户读取。
     */
    static void requireScope(DataExportTaskDO task, Object query, MerchantExportScopeSnapshot scope) {
        if (query == null || scope == null || !StringUtils.hasText(task.getMerchantId())
                || !task.getMerchantId().equals(scope.getMerchantId())) {
            throw new IllegalStateException("merchant settlement export task scope is invalid");
        }
    }

    /**
     * 解析任务冻结语言，空值和未知语言回退简体中文。
     */
    static Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }

    /**
     * 将结算协议值转换为任务冻结语言，缺少翻译时保留协议原值。
     */
    static String settlementLabel(ExcelI18nMessageResolver resolver,
                                  String category,
                                  String value,
                                  Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        String key = "excel.settlement.enum." + category + "." + normalized;
        String resolved = resolver.resolve(key, locale);
        return key.equals(resolved) ? normalized : resolved;
    }
}
