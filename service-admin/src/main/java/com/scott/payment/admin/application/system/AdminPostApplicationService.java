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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPostApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Admin Post Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminPostApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminPostService adminPostService;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final ExcelExportService excelExportService;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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
    /**
     * 查询系统管理列表或分页数据，供页面筛选和展示使用。
     * @param pageNo 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param pageSize 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param postCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param postName 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public PageResult<SysPostDO> pagePosts(int pageNo, int pageSize, String postCode, String postName, Integer status) {
        return adminPostService.pagePosts(pageNo, pageSize, postCode, postName, status);
    }

    /**
     * 查询全部启用岗位。
     *
     * @return 启用岗位列表
     */
    /**
     * 查询系统管理列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 获取系统管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysPostDO getPost(Long id) {
        return adminPostService.getPost(id);
    }

    /**
     * 导出岗位列表。
     *
     * @return 岗位列表
     */
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param operator 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param response 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void exportPosts(String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<SysPostExportRow> rows = adminPostService.exportPosts().stream()
                .map(post -> toExportRow(post, locale))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<SysPostExportRow>builder()
                        .fileName("岗位列表_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName("岗位列表")
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
    /**
     * 创建或保存系统管理数据，保持请求校验、默认值和审计字段一致。
     * @param post 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param input 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysPostDO updatePost(Long id, SysPostDO input) {
        return adminPostService.updatePost(id, input);
    }

    /**
     * 逻辑删除岗位。
     *
     * @param id 主键
     */
    /**
     * 删除系统管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
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
