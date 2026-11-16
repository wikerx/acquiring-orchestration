package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.model.ExcelDynamicPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelDynamicColumnDefinition;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

/**
 * 异步 Excel 文件生成器，统一处理分页进度、临时文件、摘要、对象键和保留期限。
 */
@Component
public class ExcelExportArtifactWriter {
    private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final ExcelExportService excelExportService;
    private final DataExportProperties properties;

    /**
     * 创建共享异步 Excel 文件生成器。
     *
     * @param excelExportService Excel 分页写入服务
     * @param properties 导出容量和保留期配置
     */
    public ExcelExportArtifactWriter(ExcelExportService excelExportService,
                                     DataExportProperties properties) {
        this.excelExportService = excelExportService;
        this.properties = properties;
    }

    /**
     * 分页生成工作簿、校验文件限制并上传至私有对象存储。
     *
     * @param task 已领取任务
     * @param storage 私有对象存储
     * @param progressListener 进度监听器
     * @param now 本轮执行时间
     * @param titleKey Excel 标题国际化键
     * @param chineseSheetName 中文工作表名称
     * @param englishSheetName 英文工作表名称
     * @param rowClass 导出行模型
     * @param pageLoader 权威分页加载器
     * @param <T> 导出行类型
     * @return 最终文件元数据
     */
    public <T> ExportArtifact write(DataExportTaskDO task,
                                    ExportObjectStorageService storage,
                                    ExportProgressListener progressListener,
                                    LocalDateTime now,
                                    String titleKey,
                                    String chineseSheetName,
                                    String englishSheetName,
                                    Class<T> rowClass,
                                    IntFunction<ExportPage<T>> pageLoader) {
        return write(task, storage, progressListener, now, titleKey, chineseSheetName,
                englishSheetName, rowClass, properties.getPageSize(), pageLoader);
    }

    /**
     * 使用动态列和分页 Map 数据生成工作簿，适用于风控等不同功能拥有不同列结构的导出任务。
     *
     * @param task 已领取任务
     * @param storage 私有对象存储
     * @param progressListener 进度监听器
     * @param now 本轮执行时间
     * @param chineseTitle 中文标题
     * @param englishTitle 英文标题
     * @param columns 动态列定义
     * @param pageLoader 权威分页加载器
     * @return 最终文件元数据
     */
    public ExportArtifact writeDynamic(DataExportTaskDO task,
                                       ExportObjectStorageService storage,
                                       ExportProgressListener progressListener,
                                       LocalDateTime now,
                                       String chineseTitle,
                                       String englishTitle,
                                       List<ExcelDynamicColumnDefinition> columns,
                                       IntFunction<ExportPage<Map<String, Object>>> pageLoader) {
        Locale locale = resolveLocale(task.getLocale());
        String title = Locale.CHINESE.getLanguage().equals(locale.getLanguage())
                ? chineseTitle : englishTitle;
        AtomicLong totalRows = new AtomicLong();
        AtomicLong processedRows = new AtomicLong();
        Path file = null;
        try {
            file = Files.createTempFile("vexra-export-", ".xlsx");
            try (OutputStream output = Files.newOutputStream(file)) {
                excelExportService.exportDynamicPaged(ExcelDynamicPagedExportRequest.builder()
                        .fileName(task.getTaskName())
                        .sheetName(title)
                        .title(title)
                        .operator(task.getCreatorAccountName())
                        .exportTime(now)
                        .locale(locale)
                        .querySummary(Locale.CHINESE.getLanguage().equals(locale.getLanguage())
                                ? "查询条件已在任务创建时冻结" : "Filters were frozen when the task was created")
                        .columns(columns)
                        .pageSize(properties.getPageSize())
                        .pageLoader(pageNo -> loadPage(
                                pageLoader, pageNo, totalRows, processedRows, progressListener))
                        .build(), output);
            }
            return uploadArtifact(task, storage, now, file, processedRows.get());
        } catch (IOException exception) {
            throw new IllegalStateException("dynamic export temporary file failed", exception);
        } finally {
            deleteQuietly(file);
        }
    }

    /**
     * 使用指定内部页大小生成工作簿，供经过容量评审的大数据来源减少跨服务请求。
     *
     * @param task 已领取任务
     * @param storage 私有对象存储
     * @param progressListener 进度监听器
     * @param now 本轮执行时间
     * @param titleKey Excel 标题国际化键
     * @param chineseSheetName 中文工作表名称
     * @param englishSheetName 英文工作表名称
     * @param rowClass 导出行模型
     * @param pageSize 内部分页大小
     * @param pageLoader 权威分页加载器
     * @param <T> 导出行类型
     * @return 最终文件元数据
     */
    public <T> ExportArtifact write(DataExportTaskDO task,
                                    ExportObjectStorageService storage,
                                    ExportProgressListener progressListener,
                                    LocalDateTime now,
                                    String titleKey,
                                    String chineseSheetName,
                                    String englishSheetName,
                                    Class<T> rowClass,
                                    int pageSize,
                                    IntFunction<ExportPage<T>> pageLoader) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("export page size must be positive");
        }
        Locale locale = resolveLocale(task.getLocale());
        AtomicLong totalRows = new AtomicLong();
        AtomicLong processedRows = new AtomicLong();
        Path file = null;
        try {
            file = Files.createTempFile("vexra-export-", ".xlsx");
            try (OutputStream output = Files.newOutputStream(file)) {
                excelExportService.exportPaged(ExcelPagedExportRequest.<T>builder()
                        .fileName(task.getTaskName())
                        .sheetName(Locale.CHINESE.getLanguage().equals(locale.getLanguage())
                                ? chineseSheetName : englishSheetName)
                        .titleKey(titleKey)
                        .operator(task.getCreatorAccountName())
                        .exportTime(now)
                        .locale(locale)
                        .querySummary(Locale.CHINESE.getLanguage().equals(locale.getLanguage())
                                ? "查询条件已在任务创建时冻结" : "Filters were frozen when the task was created")
                        .rowClass(rowClass)
                        .pageSize(pageSize)
                        .pageLoader(pageNo -> loadPage(
                                pageLoader, pageNo, totalRows, processedRows, progressListener))
                        .build(), output);
            }
            return uploadArtifact(task, storage, now, file, processedRows.get());
        } catch (IOException exception) {
            throw new IllegalStateException("export temporary file failed", exception);
        } finally {
            deleteQuietly(file);
        }
    }

    /**
     * 校验临时文件并上传对象存储，统一生成文件名、摘要和保留期限。
     */
    private ExportArtifact uploadArtifact(DataExportTaskDO task,
                                          ExportObjectStorageService storage,
                                          LocalDateTime now,
                                          Path file,
                                          long processedRows) throws IOException {
        long fileSize = Files.size(file);
        if (fileSize > properties.getMaxFileSizeBytes()) {
            throw new IllegalStateException("export file exceeds configured size limit");
        }
        String fileName = safeFileName(task.getTaskName()) + "_" + FILE_TIME.format(now) + ".xlsx";
        String objectKey = "exports/admin/" + now.getYear() + "/"
                + String.format(Locale.ROOT, "%02d", now.getMonthValue()) + "/"
                + task.getTaskNo() + "/" + fileName;
        String fileSha256 = sha256(file);
        storage.put(objectKey, CONTENT_TYPE, file);
        return new ExportArtifact(fileName, CONTENT_TYPE, objectKey, fileSize, fileSha256,
                processedRows, processedRows, now.plus(properties.getFileRetention()));
    }

    /**
     * 加载一页并在交给 Excel 写入前提交累计进度。
     */
    private <T> java.util.List<T> loadPage(IntFunction<ExportPage<T>> pageLoader,
                                            int pageNo,
                                            AtomicLong totalRows,
                                            AtomicLong processedRows,
                                            ExportProgressListener progressListener) {
        ExportPage<T> page = pageLoader.apply(pageNo);
        if (page == null) {
            throw new IllegalStateException("export source page is missing");
        }
        totalRows.set(page.total());
        long processed = processedRows.addAndGet(page.records().size());
        progressListener.onProgress(processed, page.total());
        return page.records();
    }

    /**
     * 解析任务创建时冻结的语言，未知语言回退简体中文。
     */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }

    /**
     * 清理对象键和下载文件名中的系统保留字符。
     */
    private String safeFileName(String value) {
        String normalized = value == null || value.isBlank() ? "export" : value.trim();
        return normalized.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 对最终文件计算十六进制 SHA-256 摘要。
     */
    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 尽力删除临时文件；失败不覆盖原始导出异常。
     */
    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 临时目录由操作系统和容器生命周期继续兜底清理。
        }
    }
}
