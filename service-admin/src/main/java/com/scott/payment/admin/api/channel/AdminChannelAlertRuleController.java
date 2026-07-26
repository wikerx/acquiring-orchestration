package com.scott.payment.admin.api.channel;

import com.scott.payment.admin.application.channel.AdminChannelAlertApplicationService;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.AlertStatusRequest;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleBatchSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleDimensionResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleDimensionSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleOptionsResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleQuery;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleResponse;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertRuleSaveRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelAlertRuleController
 * @date : 2026-07-17 00:00
 * @email : scott_x@163.com
 * @description : 渠道预警规则管理接口，位于 service-admin 接口层，仅提供后台规则配置能力。
 * @status : create
 */
@RestController
@RequestMapping("/admin/channel/alert-rules")
public class AdminChannelAlertRuleController {

    /**
     * channel Alert Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminChannelAlertApplicationService channelAlertApplicationService;

    /**
     * 创建渠道预警规则管理接口。
     *
     * @param channelAlertApplicationService 渠道预警应用服务
     */
    public AdminChannelAlertRuleController(AdminChannelAlertApplicationService channelAlertApplicationService) {
        this.channelAlertApplicationService = channelAlertApplicationService;
    }

    /**
     * 分页查询渠道预警规则。
     *
     * @param query 查询条件
     * @return 规则分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("channel:alert-rule:list")
    public CommonResult<PageResult<ChannelAlertRuleResponse>> pageRules(@RequestBody(required = false) ChannelAlertRuleQuery query) {
        return success(channelAlertApplicationService.pageRules(query));
    }

    /**
     * 查询渠道预警规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("channel:alert-rule:detail")
    public CommonResult<ChannelAlertRuleResponse> getRule(@PathVariable("id") Long id) {
        return success(channelAlertApplicationService.getRule(id));
    }

    /**
     * 查询渠道预警规则表单选项。
     *
     * @param channelId 渠道 ID
     * @param businessType 业务类型
     * @param keyword 搜索关键字
     * @return 表单选项
     */
    @GetMapping("/options")
    @RequiresPermission("channel:alert-rule:list")
    public CommonResult<ChannelAlertRuleOptionsResponse> ruleOptions(@RequestParam(value = "channelId", required = false) Long channelId,
                                                                     @RequestParam(value = "businessType", required = false) String businessType,
                                                                     @RequestParam(value = "keyword", required = false) String keyword) {
        return success(channelAlertApplicationService.ruleOptions(channelId, businessType, keyword));
    }

    /**
     * 查询同一渠道维度下的预警规则集合。
     *
     * @param id 规则 ID
     * @return 维度详情
     */
    @GetMapping("/{id}/dimension")
    @RequiresPermission("channel:alert-rule:detail")
    public CommonResult<ChannelAlertRuleDimensionResponse> getRuleDimension(@PathVariable("id") Long id) {
        return success(channelAlertApplicationService.getRuleDimension(id));
    }

    /**
     * 新增渠道预警规则。
     *
     * @param request 保存请求
     * @return 创建后的规则
     */
    @PostMapping
    @RequiresPermission("channel:alert-rule:add")
    @OperationLog(moduleName = "渠道预警规则", businessType = OperationTypeConstants.CREATE, operation = "新增渠道预警规则")
    public CommonResult<ChannelAlertRuleResponse> createRule(@Valid @RequestBody ChannelAlertRuleSaveRequest request) {
        return success(channelAlertApplicationService.createRule(request));
    }

    /**
     * 批量新增渠道预警规则。
     *
     * @param request 批量保存请求
     * @return 创建后的规则集合
     */
    @PostMapping("/batch")
    @RequiresPermission("channel:alert-rule:add")
    @OperationLog(moduleName = "渠道预警规则", businessType = OperationTypeConstants.CREATE, operation = "批量新增渠道预警规则")
    public CommonResult<List<ChannelAlertRuleResponse>> createRules(@Valid @RequestBody ChannelAlertRuleBatchSaveRequest request) {
        return success(channelAlertApplicationService.createRules(request));
    }

    /**
     * 修改渠道预警规则。
     *
     * @param id 规则 ID
     * @param request 保存请求
     * @return 更新后的规则
     */
    @PutMapping("/{id}")
    @RequiresPermission("channel:alert-rule:edit")
    @OperationLog(moduleName = "渠道预警规则", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道预警规则")
    public CommonResult<ChannelAlertRuleResponse> updateRule(@PathVariable("id") Long id,
                                                             @Valid @RequestBody ChannelAlertRuleSaveRequest request) {
        return success(channelAlertApplicationService.updateRule(id, request));
    }

    /**
     * 按渠道维度修改渠道预警规则集合。
     *
     * @param id 规则 ID
     * @param request 维度保存请求
     * @return 更新后的维度详情
     */
    @PutMapping("/{id}/dimension")
    @RequiresPermission("channel:alert-rule:edit")
    @OperationLog(moduleName = "渠道预警规则", businessType = OperationTypeConstants.UPDATE, operation = "维度修改渠道预警规则")
    public CommonResult<ChannelAlertRuleDimensionResponse> updateRuleDimension(@PathVariable("id") Long id,
                                                                               @Valid @RequestBody ChannelAlertRuleDimensionSaveRequest request) {
        return success(channelAlertApplicationService.updateRuleDimension(id, request));
    }

    /**
     * 更新渠道预警规则状态。
     *
     * @param id 规则 ID
     * @param request 状态请求
     * @return 更新后的规则
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("channel:alert-rule:status")
    @OperationLog(moduleName = "渠道预警规则", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道预警规则状态")
    public CommonResult<ChannelAlertRuleResponse> updateRuleStatus(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody AlertStatusRequest request) {
        return success(channelAlertApplicationService.updateRuleStatus(id, request.getStatus()));
    }

    /**
     * 删除渠道预警规则。
     *
     * @param id 规则 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("channel:alert-rule:remove")
    @OperationLog(moduleName = "渠道预警规则", businessType = OperationTypeConstants.DELETE, operation = "删除渠道预警规则")
    public CommonResult<Void> deleteRule(@PathVariable("id") Long id) {
        channelAlertApplicationService.deleteRule(id);
        return success();
    }
}
