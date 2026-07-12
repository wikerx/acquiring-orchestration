package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysDeptDTO;
import com.scott.payment.admin.dto.export.SysDeptExportRow;
import com.scott.payment.admin.service.AdminDeptService;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.db.auth.entity.SysDeptDO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDeptApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台部门管理应用服务
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDeptApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Admin Dept Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminDeptApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminDeptService adminDeptService;
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
     * 创建后台部门应用服务。
     *
     * @param adminDeptService 部门领域服务
     * @param excelExportService Excel 导出服务
     */
    public AdminDeptApplicationService(AdminDeptService adminDeptService,
                                       ExcelExportService excelExportService,
                                       ExcelI18nMessageResolver excelI18nMessageResolver,
                                       ExcelLocaleResolver excelLocaleResolver) {
        this.adminDeptService = adminDeptService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 查询部门树。
     *
     * @return 树形部门列表
     */
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public List<SysDeptDTO> tree() {
        return adminDeptService.tree();
    }

    /**
     * 查询部门详情。
     *
     * @param id 部门主键
     * @return 部门详情
     */
    /**
     * 获取系统管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysDeptDO getDept(Long id) {
        return adminDeptService.getDept(id);
    }

    /**
     * 导出全部部门资料。
     *
     * @return 部门列表
     */
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param operator 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param response 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void exportDepts(String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<SysDeptDO> departments = adminDeptService.exportDepts();
        Map<Long, String> deptNameMap = new LinkedHashMap<>();
        for (SysDeptDO dept : departments) {
            deptNameMap.put(dept.getId(), dept.getDeptName());
        }
        List<SysDeptExportRow> rows = departments.stream()
                .map(dept -> toExportRow(dept, deptNameMap, locale))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<SysDeptExportRow>builder()
                        .fileName(excelI18nMessageResolver.resolve("excel.dept.title", locale) + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName(excelI18nMessageResolver.resolve("excel.dept.title", locale))
                        .titleKey("excel.dept.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(excelI18nMessageResolver.resolve("excel.common.noCondition", locale))
                        .rowClass(SysDeptExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 新增部门。
     *
     * @param dept 部门实体
     * @return 保存后的部门
     */
    /**
     * 创建或保存系统管理数据，保持请求校验、默认值和审计字段一致。
     * @param dept 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysDeptDO createDept(SysDeptDO dept) {
        return adminDeptService.createDept(dept);
    }

    /**
     * 更新部门。
     *
     * @param id    部门主键
     * @param input 更新输入
     * @return 更新后的部门
     */
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param input 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysDeptDO updateDept(Long id, SysDeptDO input) {
        return adminDeptService.updateDept(id, input);
    }

    /**
     * 逻辑删除部门。
     *
     * @param id 部门主键
     */
    /**
     * 删除系统管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void removeDept(Long id) {
        adminDeptService.removeDept(id);
    }

    /**
     * 将部门实体转换为导出行，补齐上级部门名称和状态文案。
     *
     * @param dept 当前部门实体
     * @param deptNameMap 部门名称索引
     * @param locale 当前语言
     * @return 导出行对象
     */
    private SysDeptExportRow toExportRow(SysDeptDO dept, Map<Long, String> deptNameMap, Locale locale) {
        SysDeptExportRow row = new SysDeptExportRow();
        row.setDeptName(dept.getDeptName());
        row.setParentName(dept.getParentId() == null || dept.getParentId() == 0
                ? "-"
                : deptNameMap.getOrDefault(dept.getParentId(), "-"));
        row.setSortNo(dept.getSortNo());
        row.setLeader(dept.getLeader());
        row.setPhone(dept.getPhone());
        row.setEmail(dept.getEmail());
        row.setStatus(excelI18nMessageResolver.resolve(
                dept.getStatus() != null && dept.getStatus() == 1 ? "excel.common.enabled" : "excel.common.disabled",
                locale
        ));
        return row;
    }
}
