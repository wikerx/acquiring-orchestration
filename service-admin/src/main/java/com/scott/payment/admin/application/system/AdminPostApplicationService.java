package com.scott.payment.admin.application.system;

import com.scott.payment.admin.converter.PostConverter;
import com.scott.payment.admin.dto.export.SysPostExportRow;
import com.scott.payment.admin.dto.system.PostDTOs.Response;
import com.scott.payment.admin.dto.system.PostDTOs.SaveRequest;
import com.scott.payment.admin.service.AdminPostService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.db.auth.entity.SysPostDO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 管理后台岗位管理应用服务
 */
@Service
public class AdminPostApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminPostService adminPostService;
    private final PostConverter postConverter;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建后台岗位应用服务。
     *
     * @param adminPostService 岗位领域服务
     * @param postConverter 岗位接口模型转换器
     * @param excelExportService Excel 导出服务
     * @param excelI18nMessageResolver Excel 国际化文案解析器
     * @param excelLocaleResolver Excel 语言解析器
     */
    public AdminPostApplicationService(AdminPostService adminPostService,
                                       PostConverter postConverter,
                                       ExcelExportService excelExportService,
                                       ExcelI18nMessageResolver excelI18nMessageResolver,
                                       ExcelLocaleResolver excelLocaleResolver) {
        this.adminPostService = adminPostService;
        this.postConverter = postConverter;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 分页查询岗位列表。
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @param postCode 岗位编码
     * @param postName 岗位名称
     * @param status   状态
     * @return 岗位分页结果
     */
    public PageResult<Response> pagePosts(int pageNo, int pageSize, String postCode, String postName, Integer status) {
        PageResult<SysPostDO> page = adminPostService.pagePosts(pageNo, pageSize, postCode, postName, status);
        return PageResult.of(page.getTotal(), page.getPageNo(), page.getPageSize(),
                postConverter.toResponses(page.getRecords()));
    }

    /**
     * 查询全部启用岗位。
     *
     * @return 启用岗位列表
     */
    public List<Response> listEnabledPosts() {
        return postConverter.toResponses(adminPostService.listEnabledPosts());
    }

    /**
     * 查询岗位详情。
     *
     * @param id 主键
     * @return 岗位详情
     */
    public Response getPost(Long id) {
        return postConverter.toResponse(adminPostService.getPost(id));
    }

    /**
     * 导出岗位列表。
     *
     * @return 岗位列表
     */
    public void exportPosts(String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<SysPostExportRow> rows = adminPostService.exportPosts().stream()
                .map(post -> toExportRow(post, locale))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<SysPostExportRow>builder()
                        .fileName(excelI18nMessageResolver.resolve("excel.post.title", locale) + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName(excelI18nMessageResolver.resolve("excel.post.title", locale))
                        .titleKey("excel.post.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(excelI18nMessageResolver.resolve("excel.common.noCondition", locale))
                        .rowClass(SysPostExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 新增岗位。
     *
     * @param request 岗位保存请求
     * @return 保存后的岗位响应
     */
    public Response createPost(SaveRequest request) {
        SysPostDO post = adminPostService.createPost(postConverter.toEntity(request));
        return postConverter.toResponse(post);
    }

    /**
     * 更新岗位。
     *
     * @param id    主键
     * @param request 更新输入
     * @return 更新后的岗位响应
     */
    public Response updatePost(Long id, SaveRequest request) {
        SysPostDO post = adminPostService.updatePost(id, postConverter.toEntity(request));
        return postConverter.toResponse(post);
    }

    /**
     * 逻辑删除岗位。
     *
     * @param id 主键
     */
    public void removePost(Long id) {
        adminPostService.removePost(id);
    }

    /**
     * 将岗位实体转换为导出行，避免直接暴露数据库对象结构。
     *
     * @param post 岗位实体
     * @param locale 当前语言
     * @return 导出行对象
     */
    private SysPostExportRow toExportRow(SysPostDO post, Locale locale) {
        SysPostExportRow row = new SysPostExportRow();
        row.setPostCode(post.getPostCode());
        row.setPostName(post.getPostName());
        row.setSortNo(post.getSortNo());
        row.setStatus(excelI18nMessageResolver.resolve(
                post.getStatus() != null && post.getStatus() == 1 ? "excel.common.enabled" : "excel.common.disabled",
                locale
        ));
        row.setRemark(post.getRemark());
        row.setCreatedAt(post.getCreatedAt());
        return row;
    }
}
