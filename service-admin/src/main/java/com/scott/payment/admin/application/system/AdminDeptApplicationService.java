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
@Service
public class AdminDeptApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * admin Dept Service 依赖，用于 Admin Dept Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminDeptService adminDeptService;
    /**
     * excel Export Service 依赖，用于 Admin Dept Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelExportService excelExportService;
    /**
     * excel I 18 n Message Resolver，用于保存 Admin Dept Application Service 中与 exceli18nmessageresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    /**
     * excel Locale Resolver，用于保存 Admin Dept Application Service 中与 excellocaleresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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
    public List<SysDeptDTO> tree() {
        return adminDeptService.tree();
    }

    /**
     * 查询部门详情。
     *
     * @param id 部门主键
     * @return 部门详情
     */
    public SysDeptDO getDept(Long id) {
        return adminDeptService.getDept(id);
    }

    /**
     * 导出全部部门资料。
     *
     * @return 部门列表
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
    public SysDeptDO updateDept(Long id, SysDeptDO input) {
        return adminDeptService.updateDept(id, input);
    }

    /**
     * 逻辑删除部门。
     *
     * @param id 部门主键
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
