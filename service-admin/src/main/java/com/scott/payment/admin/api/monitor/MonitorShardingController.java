package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminShardingGovernanceApplicationService;
import com.scott.payment.admin.dto.monitor.ShardingIdRuleResponse;
import com.scott.payment.admin.dto.monitor.ShardingPhysicalTableQueryRequest;
import com.scott.payment.admin.dto.monitor.ShardingPhysicalTableResponse;
import com.scott.payment.admin.dto.monitor.ShardingRuleResponse;
import com.scott.payment.admin.dto.monitor.ShardingTableCreateLogQueryRequest;
import com.scott.payment.admin.dto.monitor.ShardingTableCreateLogResponse;
import com.scott.payment.admin.dto.monitor.ShardingTableCreateRequest;
import com.scott.payment.admin.dto.monitor.ShardingTablePreCreateResultResponse;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

@RestController
@RequestMapping("/admin/monitor/sharding")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorShardingController
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Monitor Sharding Controller 控制器，位于 运营后台服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class MonitorShardingController {

    /**
     * admin Sharding Governance Application Service 依赖，用于 Monitor Sharding Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminShardingGovernanceApplicationService adminShardingGovernanceApplicationService;

    /**
     * 创建分表治理控制器。
     *
     * @param adminShardingGovernanceApplicationService 分表治理应用服务
     */
    public MonitorShardingController(AdminShardingGovernanceApplicationService adminShardingGovernanceApplicationService) {
        this.adminShardingGovernanceApplicationService = adminShardingGovernanceApplicationService;
    }

    /**
     * 查询分表规则列表。
     *
     * @return 分表规则列表
     */
    @GetMapping("/rules")
    @RequiresPermission("monitor:sharding:rule:list")
    public CommonResult<List<ShardingRuleResponse>> rules() {
        return success(adminShardingGovernanceApplicationService.listRules());
    }

    /**
     * 查询分表规则详情。
     *
     * @param logicalTable 逻辑表或规则 key
     * @return 分表规则详情
     */
    @GetMapping("/rules/{logicalTable}")
    @RequiresPermission("monitor:sharding:rule:query")
    public CommonResult<ShardingRuleResponse> rule(@PathVariable("logicalTable") String logicalTable) {
        return success(adminShardingGovernanceApplicationService.getRule(logicalTable));
    }

    /**
     * 分页查询物理表登记。
     *
     * @param request 查询条件
     * @return 物理表分页结果
     */
    @PostMapping("/physical-tables/search")
    @RequiresPermission("monitor:sharding:physical:list")
    public CommonResult<PageResult<ShardingPhysicalTableResponse>> physicalTables(
            @RequestBody(required = false) ShardingPhysicalTableQueryRequest request) {
        return success(adminShardingGovernanceApplicationService.pagePhysicalTables(request));
    }

    /**
     * 查询物理表登记详情。
     *
     * @param id 物理表登记主键
     * @return 物理表详情
     */
    @GetMapping("/physical-tables/{id}")
    @RequiresPermission("monitor:sharding:physical:query")
    public CommonResult<ShardingPhysicalTableResponse> physicalTable(@PathVariable("id") Long id) {
        return success(adminShardingGovernanceApplicationService.getPhysicalTable(id));
    }

    /**
     * 刷新分表物理表登记状态。
     *
     * <p>该操作只探测目标物理表是否存在并刷新治理表登记，不执行 DDL。</p>
     *
     * @param request 刷新范围
     * @return 刷新结果
     */
    @PostMapping("/physical-tables/refresh")
    @RequiresPermission("monitor:sharding:physical:refresh")
    @OperationLog(moduleName = "分表治理", businessType = OperationTypeConstants.QUERY, operation = "刷新分表物理表登记")
    public CommonResult<ShardingTablePreCreateResultResponse> refreshPhysicalTables(
            @RequestBody(required = false) ShardingTableCreateRequest request) {
        return success(adminShardingGovernanceApplicationService.refreshPhysicalTables(request, currentOperatorId(), currentOperatorName()));
    }

    /**
     * 检查分表物理表结构。
     *
     * <p>该操作复用预建表 dry-run 链路，对已存在物理表与模板表结构做比对，不执行 DDL。</p>
     *
     * @param request 检查范围
     * @return 检查结果
     */
    @PostMapping("/physical-tables/check-schema")
    @RequiresPermission("monitor:sharding:physical:check")
    @OperationLog(moduleName = "分表治理", businessType = OperationTypeConstants.QUERY, operation = "检查分表物理表结构")
    public CommonResult<ShardingTablePreCreateResultResponse> checkPhysicalTableSchema(
            @RequestBody(required = false) ShardingTableCreateRequest request) {
        return success(adminShardingGovernanceApplicationService.checkPhysicalTableSchema(request, currentOperatorId(), currentOperatorName()));
    }

    /**
     * 分页查询建表任务日志。
     *
     * @param request 查询条件
     * @return 建表日志分页结果
     */
    @PostMapping("/table-create/logs/search")
    @RequiresPermission("monitor:sharding:task:list")
    public CommonResult<PageResult<ShardingTableCreateLogResponse>> createLogs(
            @RequestBody(required = false) ShardingTableCreateLogQueryRequest request) {
        return success(adminShardingGovernanceApplicationService.pageCreateLogs(request));
    }

    /**
     * 查询建表任务日志详情。
     *
     * @param id 建表日志主键
     * @return 建表日志详情
     */
    @GetMapping("/table-create/logs/{id}")
    @RequiresPermission("monitor:sharding:task:query")
    public CommonResult<ShardingTableCreateLogResponse> createLog(@PathVariable("id") Long id) {
        return success(adminShardingGovernanceApplicationService.getCreateLog(id));
    }

    /**
     * 预演分表物理表预创建。
     *
     * @param request 建表请求
     * @return 预演结果
     */
    @PostMapping("/table-create/dry-run")
    @RequiresPermission("monitor:sharding:task:dryRun")
    @OperationLog(moduleName = "分表治理", businessType = OperationTypeConstants.QUERY, operation = "预演分表物理表创建")
    public CommonResult<ShardingTablePreCreateResultResponse> dryRun(@RequestBody(required = false) ShardingTableCreateRequest request) {
        return success(adminShardingGovernanceApplicationService.dryRun(request, currentOperatorId(), currentOperatorName()));
    }

    /**
     * 立即创建缺失的分表物理表。
     *
     * @param request 建表请求
     * @return 建表结果
     */
    @PostMapping("/table-create/execute")
    @RequiresPermission("monitor:sharding:task:execute")
    @OperationLog(moduleName = "分表治理", businessType = OperationTypeConstants.CREATE, operation = "立即创建分表物理表")
    public CommonResult<ShardingTablePreCreateResultResponse> execute(@RequestBody(required = false) ShardingTableCreateRequest request) {
        return success(adminShardingGovernanceApplicationService.execute(request, currentOperatorId(), currentOperatorName()));
    }

    /**
     * 查询分表 ID 规则说明。
     *
     * @return ID 规则说明
     */
    @GetMapping("/id-rule")
    @RequiresPermission("monitor:sharding:idRule:query")
    public CommonResult<ShardingIdRuleResponse> idRule() {
        return success(adminShardingGovernanceApplicationService.idRule());
    }

    /**
     * 解析分表治理操作审计使用的稳定操作人标识。
     *
     * @return 优先返回账号主键或用户主键，其次登录账号；无认证上下文时返回 {@code null}
     */
    private String currentOperatorId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return null;
        }
        Long operatorId = account.getAccountId() == null ? account.getUserId() : account.getAccountId();
        return operatorId == null ? account.getLoginAccount() : String.valueOf(operatorId);
    }

    /**
     * 解析分表治理操作审计使用的操作人名称。
     *
     * @return 优先返回真实姓名，其次登录账号；均不可用时返回 {@code admin}
     */
    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount() == null ? "admin" : account.getLoginAccount();
    }
}
