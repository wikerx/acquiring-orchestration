package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 汇率配置与预警异步导出处理器基类，统一查询快照解析、分页写入和国际化回退。
 *
 * @param <Q> 冻结查询类型
 * @param <R> Excel 行类型
 */
abstract class AbstractExchangeExportProcessor<Q, R> implements ExportTaskProcessor {
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建汇率配置与预警异步导出处理器。
     *
     * @param artifactWriter 分页 Excel 文件生成器
     * @param messageResolver Excel 国际化解析器
     * @param properties 导出分页和容量配置
     */
    protected AbstractExchangeExportProcessor(ExcelExportArtifactWriter artifactWriter,
                                              ExcelI18nMessageResolver messageResolver,
                                              DataExportProperties properties) {
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 解析任务创建时冻结的查询并分页生成私有 Excel 文件。
     *
     * @param task 已领取任务
     * @param storage 私有对象存储
     * @param progressListener 分页进度监听器
     * @param now 本轮执行时间
     * @return 已上传文件元数据
     */
    @Override
    public final ExportArtifact process(DataExportTaskDO task, ExportObjectStorageService storage,
                                        ExportProgressListener progressListener, LocalDateTime now) {
        Q query = JsonUtils.parseObject(task.getQuerySnapshot(), queryClass());
        if (query == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                titleKey(), defaultChineseTitle(), defaultEnglishTitle(), rowClass(), properties.getPageSize(),
                pageNo -> loadPage(task, query, pageNo, locale));
    }

    /**
     * 返回冻结查询的运行时类型。
     *
     * @return 查询类型
     */
    protected abstract Class<Q> queryClass();

    /**
     * 返回 Excel 行的运行时类型。
     *
     * @return Excel 行类型
     */
    protected abstract Class<R> rowClass();

    /**
     * 返回 Excel 标题国际化键。
     *
     * @return 标题键
     */
    protected abstract String titleKey();

    /**
     * 返回标题缺失时的中文回退值。
     *
     * @return 中文标题
     */
    protected abstract String defaultChineseTitle();

    /**
     * 返回标题缺失时的英文回退值。
     *
     * @return 英文标题
     */
    protected abstract String defaultEnglishTitle();

    /**
     * 使用任务创建人和服务端分页读取一页权威投影。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 服务端页码
     * @param locale 冻结语言
     * @return Excel 行分页
     */
    protected abstract ExportPage<R> loadPage(DataExportTaskDO task, Q query, int pageNo, Locale locale);

    /**
     * 返回统一配置的内部导出分页大小。
     *
     * @return 单页条数
     */
    protected final int pageSize() {
        return properties.getPageSize();
    }

    /**
     * 解析汇率枚举展示值，缺少翻译时保留协议原值。
     *
     * @param category 枚举分类
     * @param value 协议原值
     * @param locale 冻结语言
     * @return 国际化展示值或协议原值
     */
    protected final String label(String category, Object value, Locale locale) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return "";
        }
        String normalized = String.valueOf(value).trim();
        String key = "excel.exchange.enum." + category + "." + normalized;
        String resolved = messageResolver.resolve(key, locale);
        return key.equals(resolved) ? normalized : resolved;
    }

    /**
     * 将数据库布尔标识转换为当前语言的可读值。
     *
     * @param value 1 表示是，其他值表示否
     * @param locale 冻结语言
     * @return 本地化布尔文本
     */
    protected final String yesNo(Integer value, Locale locale) {
        return label("yesNo", value != null && value == 1 ? "YES" : "NO", locale);
    }

    /**
     * 解析任务冻结语言，未知语言回退简体中文。
     *
     * @param languageTag IETF 语言标签
     * @return Excel 使用的语言环境
     */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
