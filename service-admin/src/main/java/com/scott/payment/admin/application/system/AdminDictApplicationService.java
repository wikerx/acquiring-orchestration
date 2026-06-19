package com.scott.payment.admin.application.system;

import com.scott.payment.admin.converter.DictConverter;
import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.SysDictDataSaveRequest;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.SysDictTypeQueryRequest;
import com.scott.payment.admin.dto.SysDictTypeSaveRequest;
import com.scott.payment.admin.dto.export.SysDictDataExportRow;
import com.scott.payment.admin.dto.export.SysDictTypeExportRow;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDictApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台数据字典应用服务
 * @status : create
 */
@Service
public class AdminDictApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AdminDictService adminDictService;
    private final ExcelExportService excelExportService;

    /**
     * 创建后台数据字典应用服务。
     *
     * @param adminDictService 数据字典领域服务
     * @param excelExportService Excel 导出服务
     */
    public AdminDictApplicationService(AdminDictService adminDictService,
                                       ExcelExportService excelExportService) {
        this.adminDictService = adminDictService;
        this.excelExportService = excelExportService;
    }

    /**
     * 保存字典类型。
     *
     * @param request 保存请求
     * @return 字典类型详情
     */
    public SysDictTypeDTO saveDictType(SysDictTypeSaveRequest request) {
        return adminDictService.saveDictType(request);
    }

    /**
     * 分页查询字典类型。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResult<SysDictTypeDTO> pageDictTypes(SysDictTypeQueryRequest request) {
        return adminDictService.pageDictTypes(request);
    }

    /**
     * 导出字典类型列表。
     *
     * @param request 查询条件
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportDictTypes(SysDictTypeQueryRequest request,
                                String operator,
                                HttpServletResponse response) {
        List<SysDictTypeExportRow> rows = adminDictService.listDictTypes(request).stream()
                .map(DictConverter.INSTANCE::toTypeExportRow)
                .peek(this::fillDictTypeDisplayValue)
                .toList();
        excelExportService.export(
                ExcelExportRequest.<SysDictTypeExportRow>builder()
                        .fileName("字典类型_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName("字典类型")
                        .titleKey("excel.dict.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(Locale.SIMPLIFIED_CHINESE)
                        .querySummary(buildDictTypeQuerySummary(request))
                        .rowClass(SysDictTypeExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 删除字典类型。
     *
     * @param dictType 字典类型编码
     */
    public void deleteDictType(String dictType) {
        adminDictService.deleteDictType(dictType);
    }

    /**
     * 保存字典数据。
     *
     * @param request 保存请求
     * @return 字典数据详情
     */
    public SysDictDataDTO saveDictData(SysDictDataSaveRequest request) {
        return adminDictService.saveDictData(request);
    }

    /**
     * 分页查询字典数据。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResult<SysDictDataDTO> pageDictData(SysDictDataQueryRequest request) {
        return adminDictService.pageDictData(request);
    }

    /**
     * 导出字典数据列表。
     *
     * @param request 查询条件
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportDictData(SysDictDataQueryRequest request,
                               String operator,
                               HttpServletResponse response) {
        List<SysDictDataExportRow> rows = adminDictService.listDictData(request).stream()
                .map(DictConverter.INSTANCE::toDataExportRow)
                .peek(this::fillDictDataDisplayValue)
                .toList();
        excelExportService.export(
                ExcelExportRequest.<SysDictDataExportRow>builder()
                        .fileName("字典数据_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName("字典数据")
                        .titleKey("excel.dictData.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(Locale.SIMPLIFIED_CHINESE)
                        .querySummary(buildDictDataQuerySummary(request))
                        .rowClass(SysDictDataExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 按主键查询字典数据详情。
     *
     * @param id 字典数据主键
     * @return 字典数据详情
     */
    public SysDictDataDTO getDictDataById(Long id) {
        return adminDictService.getDictDataById(id);
    }

    /**
     * 按主键更新字典数据。
     *
     * @param id      字典数据主键
     * @param request 保存请求
     * @return 更新后的字典数据
     */
    public SysDictDataDTO updateDictDataById(Long id, SysDictDataSaveRequest request) {
        return adminDictService.updateDictDataById(id, request);
    }

    /**
     * 删除字典数据。
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典值
     * @param locale    语言区域
     */
    public void deleteDictData(String dictType, String dictValue, String locale) {
        adminDictService.deleteDictData(dictType, dictValue, locale);
    }

    /**
     * 按主键删除字典数据。
     *
     * @param id 字典数据主键
     */
    public void deleteDictDataById(Long id) {
        adminDictService.deleteDictDataById(id);
    }

    /**
     * 填充字典类型导出展示文案。
     *
     * @param row 导出行对象
     */
    private void fillDictTypeDisplayValue(SysDictTypeExportRow row) {
        row.setStatus("1".equals(row.getStatus()) ? "启用" : "停用");
        row.setSystemBuiltin("1".equals(row.getSystemBuiltin()) ? "是" : "否");
    }

    /**
     * 填充字典数据导出展示文案。
     *
     * @param row 导出行对象
     */
    private void fillDictDataDisplayValue(SysDictDataExportRow row) {
        row.setStatus("1".equals(row.getStatus()) ? "启用" : "停用");
        row.setDefaultFlag("1".equals(row.getDefaultFlag()) ? "是" : "否");
    }

    /**
     * 构建字典类型导出摘要。
     *
     * @param request 查询条件
     * @return 摘要
     */
    private String buildDictTypeQuerySummary(SysDictTypeQueryRequest request) {
        if (request == null) {
            return "全部数据";
        }
        StringBuilder builder = new StringBuilder();
        if (request.getDictName() != null && !request.getDictName().isBlank()) {
            builder.append("字典名称=").append(request.getDictName().trim());
        }
        if (request.getDictType() != null && !request.getDictType().isBlank()) {
            appendSeparator(builder);
            builder.append("字典类型=").append(request.getDictType().trim());
        }
        if (request.getBizDomain() != null && !request.getBizDomain().isBlank()) {
            appendSeparator(builder);
            builder.append("业务域=").append(request.getBizDomain().trim());
        }
        if (request.getStatus() != null) {
            appendSeparator(builder);
            builder.append("状态=").append(request.getStatus() == 1 ? "启用" : "停用");
        }
        return builder.isEmpty() ? "全部数据" : builder.toString();
    }

    /**
     * 构建字典数据导出摘要。
     *
     * @param request 查询条件
     * @return 摘要
     */
    private String buildDictDataQuerySummary(SysDictDataQueryRequest request) {
        if (request == null) {
            return "全部数据";
        }
        StringBuilder builder = new StringBuilder();
        if (request.getDictType() != null && !request.getDictType().isBlank()) {
            builder.append("字典类型=").append(request.getDictType().trim());
        }
        if (request.getDictLabel() != null && !request.getDictLabel().isBlank()) {
            appendSeparator(builder);
            builder.append("字典标签=").append(request.getDictLabel().trim());
        }
        if (request.getLocale() != null && !request.getLocale().isBlank()) {
            appendSeparator(builder);
            builder.append("语言=").append(request.getLocale().trim());
        }
        if (request.getStatus() != null) {
            appendSeparator(builder);
            builder.append("状态=").append(request.getStatus() == 1 ? "启用" : "停用");
        }
        return builder.isEmpty() ? "全部数据" : builder.toString();
    }

    /**
     * 为查询摘要补充分隔符。
     *
     * @param builder 摘要构造器
     */
    private void appendSeparator(StringBuilder builder) {
        if (!builder.isEmpty()) {
            builder.append("，");
        }
    }
}
