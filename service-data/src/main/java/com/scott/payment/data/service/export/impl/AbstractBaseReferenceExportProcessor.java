package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 管理端基础数据异步导出的公共执行骨架，统一校验快照、语言和分页容量。
 *
 * @param <Q> 强类型查询快照
 * @param <R> 独立 Excel 行模型
 */
abstract class AbstractBaseReferenceExportProcessor<Q, R> implements ExportTaskProcessor {
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建基础数据异步导出处理器公共依赖。
     *
     * @param artifactWriter 统一分页 Excel 写入器
     * @param messageResolver Excel 国际化解析器
     * @param properties 异步导出容量配置
     */
    protected AbstractBaseReferenceExportProcessor(
            ExcelExportArtifactWriter artifactWriter,
            ExcelI18nMessageResolver messageResolver,
            DataExportProperties properties) {
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 解析任务冻结快照并按平台级分页容量生成 Excel 文件。
     *
     * @param task 已领取的导出任务
     * @param storage 私有对象存储
     * @param progressListener 进度回写器
     * @param now 当前执行时刻
     * @return 已上传文件的元数据
     */
    @Override
    public final ExportArtifact process(DataExportTaskDO task,
                                        ExportObjectStorageService storage,
                                        ExportProgressListener progressListener,
                                        LocalDateTime now) {
        Q query = JsonUtils.parseObject(task.getQuerySnapshot(), queryClass());
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                titleKey(), chineseTitle(), englishTitle(), rowClass(), properties.getPlatformPageSize(),
                pageNo -> loadPage(task, query, pageNo, locale));
    }

    /**
     * 返回查询快照的反序列化类型。
     *
     * @return 强类型查询类
     */
    protected abstract Class<Q> queryClass();

    /**
     * 返回 Excel 国际化标题键。
     *
     * @return 标题键
     */
    protected abstract String titleKey();

    /**
     * 返回中文文件标题兜底值。
     *
     * @return 中文标题
     */
    protected abstract String chineseTitle();

    /**
     * 返回英文文件标题兜底值。
     *
     * @return 英文标题
     */
    protected abstract String englishTitle();

    /**
     * 返回独立 Excel 行类型。
     *
     * @return 行模型类
     */
    protected abstract Class<R> rowClass();

    /**
     * 按冻结条件读取并转换指定页。
     *
     * @param task 已领取任务
     * @param query 强类型冻结查询
     * @param pageNo 当前页码
     * @param locale 任务冻结语言
     * @return 当前页和总行数
     */
    protected abstract ExportPage<R> loadPage(
            DataExportTaskDO task, Q query, int pageNo, Locale locale);

    /**
     * 将基础数据启停状态转换为任务冻结语言，未知状态保留原值便于排查。
     *
     * @param status 数据状态
     * @param locale 任务冻结语言
     * @return 本地化状态文本
     */
    protected final String statusLabel(Integer status, Locale locale) {
        if (status == null) {
            return "";
        }
        if (status == 1) {
            return messageResolver.resolve("excel.common.enabled", locale);
        }
        if (status == 0) {
            return messageResolver.resolve("excel.common.disabled", locale);
        }
        return String.valueOf(status);
    }

    /**
     * 将布尔标记转换为任务冻结语言，空值保持为空。
     *
     * @param value 布尔标记
     * @param locale 任务冻结语言
     * @return 本地化的是或否
     */
    protected final String booleanLabel(Boolean value, Locale locale) {
        if (value == null) {
            return "";
        }
        return messageResolver.resolve(value ? "excel.common.yes" : "excel.common.no", locale);
    }

    /**
     * 将整数布尔标记转换为任务冻结语言，空值保持为空。
     *
     * @param value 0/1 标记
     * @param locale 任务冻结语言
     * @return 本地化的是或否
     */
    protected final String booleanLabel(Integer value, Locale locale) {
        return value == null ? "" : booleanLabel(value == 1, locale);
    }

    /**
     * 返回平台级导出分页大小，确保来源请求和 Excel 分页一致。
     *
     * @return 经配置校验的平台分页大小
     */
    protected final int pageSize() {
        return properties.getPlatformPageSize();
    }

    /**
     * 解析任务冻结语言，缺失或非法语言回退简体中文。
     *
     * @param languageTag IETF 语言标签
     * @return 可用于 Excel 本地化的语言
     */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null
                ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
