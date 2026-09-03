package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.export.SysPostExportRow;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPostApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台岗位管理应用服务
 * @status : create
 */
@Service
public class AdminPostApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminPostService adminPostService;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建后台岗位应用服务。
     *
     * @param adminPostService 岗位领域服务
     * @param excelExportService Excel 导出服务
     */
    public AdminPostApplicationService(AdminPostService adminPostService,
                                       ExcelExportService excelExportService,
                                       ExcelI18nMessageResolver excelI18nMessageResolver,
                                       ExcelLocaleResolver excelLocaleResolver) {
        this.adminPostService = adminPostService;
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
    public PageResult<SysPostDO> pagePosts(int pageNo, int pageSize, String postCode, String postName, Integer status) {
        return adminPostService.pagePosts(pageNo, pageSize, postCode, postName, status);
    }

    /**
     * 查询全部启用岗位。
     *
     * @return 启用岗位列表
     */
    public List<SysPostDO> listEnabledPosts() {
        return adminPostService.listEnabledPosts();
    }

    /**
     * 查询岗位详情。
     *
     * @param id 主键
     * @return 岗位详情
     */
    public SysPostDO getPost(Long id) {
        return adminPostService.getPost(id);
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
     * @param post 岗位实体
     * @return 保存后的岗位
     */
    public SysPostDO createPost(SysPostDO post) {
        return adminPostService.createPost(post);
    }

    /**
     * 更新岗位。
     *
     * @param id    主键
     * @param input 更新输入
     * @return 更新后的岗位
     */
    public SysPostDO updatePost(Long id, SysPostDO input) {
        return adminPostService.updatePost(id, input);
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
