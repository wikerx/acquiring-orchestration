package com.scott.payment.admin.api.operation;

import com.scott.payment.admin.application.operation.AdminOperationalWorkItemApplicationService;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.ActionRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.AssigneeOption;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchResponse;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemDetail;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.WorkItemHistory;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理端统一运营待办接口，不提供人工删除或直接关闭来源业务的能力。
 */
@RestController
@RequestMapping("/admin/operations/work-items")
public class AdminOperationalWorkItemController {

    private final AdminOperationalWorkItemApplicationService applicationService;

    /**
     * 创建统一运营待办管理入口。
     *
     * @param applicationService 统一待办应用服务
     */
    public AdminOperationalWorkItemController(AdminOperationalWorkItemApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询当前权限和商户数据范围内的待办分页及汇总。
     *
     * @param request 查询条件；为空时使用默认分页和当天时间范围
     * @return 待办分页及汇总
     */
    @PostMapping("/search")
    @RequiresPermission("operations:work-item:list")
    public CommonResult<SearchResponse> search(@RequestBody(required = false) SearchRequest request) {
        return success(applicationService.search(request));
    }

    /**
     * 查询单个待办的实时来源摘要和完整处理轨迹。
     *
     * @param workItemNo 待办编号
     * @return 待办详情
     */
    @GetMapping("/{workItemNo}")
    @RequiresPermission("operations:work-item:detail")
    public CommonResult<WorkItemDetail> detail(@PathVariable("workItemNo") String workItemNo) {
        return success(applicationService.detail(workItemNo));
    }

    /**
     * 分页查询单个待办的处理轨迹。
     *
     * @param workItemNo 待办编号
     * @param pageNo 页码，默认 1
     * @param pageSize 每页条数，默认 10，最大 100
     * @return 处理轨迹分页
     */
    @GetMapping("/{workItemNo}/history")
    @RequiresPermission("operations:work-item:detail")
    public CommonResult<PageResult<WorkItemHistory>> history(
            @PathVariable("workItemNo") String workItemNo,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return success(applicationService.searchHistory(workItemNo, pageNo, pageSize));
    }

    /**
     * 查询可接收分派的有效管理账号。
     *
     * @param keyword 登录账号或人员名称关键字
     * @return 可分派账号选项
     */
    @GetMapping("/assignees")
    @RequiresPermission("operations:work-item:list")
    public CommonResult<List<AssigneeOption>> assignees(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return success(applicationService.assignees(keyword));
    }

    /**
     * 当前操作人认领未分派的活动待办。
     *
     * @param workItemNo 待办编号
     * @param request 带请求幂等键和预期版本的操作命令
     * @return 更新后的待办详情
     */
    @PostMapping("/{workItemNo}/claim")
    @RequiresPermission("operations:work-item:claim")
    @OperationLog(moduleName = "统一运营待办", businessType = OperationTypeConstants.UPDATE,
            operation = "认领运营待办")
    public CommonResult<WorkItemDetail> claim(@PathVariable("workItemNo") String workItemNo,
                                              @Valid @RequestBody ActionRequest request) {
        return success(applicationService.claim(workItemNo, request));
    }

    /**
     * 将活动待办分派给有效管理账号。
     *
     * @param workItemNo 待办编号
     * @param request 带目标账号、请求幂等键和预期版本的操作命令
     * @return 更新后的待办详情
     */
    @PostMapping("/{workItemNo}/assign")
    @RequiresPermission("operations:work-item:assign")
    @OperationLog(moduleName = "统一运营待办", businessType = OperationTypeConstants.UPDATE,
            operation = "分派运营待办")
    public CommonResult<WorkItemDetail> assign(@PathVariable("workItemNo") String workItemNo,
                                               @Valid @RequestBody ActionRequest request) {
        return success(applicationService.assign(workItemNo, request));
    }

    /**
     * 将已分派活动待办转交给其他管理账号。
     *
     * @param workItemNo 待办编号
     * @param request 带目标账号、请求幂等键和预期版本的操作命令
     * @return 更新后的待办详情
     */
    @PostMapping("/{workItemNo}/transfer")
    @RequiresPermission("operations:work-item:transfer")
    @OperationLog(moduleName = "统一运营待办", businessType = OperationTypeConstants.UPDATE,
            operation = "转交运营待办")
    public CommonResult<WorkItemDetail> transfer(@PathVariable("workItemNo") String workItemNo,
                                                 @Valid @RequestBody ActionRequest request) {
        return success(applicationService.transfer(workItemNo, request));
    }

    /**
     * 人工完成活动待办，处理结论进入待办轨迹且不修改来源业务状态。
     *
     * @param workItemNo 待办编号
     * @param request 带完成原因、请求幂等键和预期版本的操作命令
     * @return 更新后的待办详情
     */
    @PostMapping("/{workItemNo}/complete")
    @RequiresPermission("operations:work-item:complete")
    @OperationLog(moduleName = "统一运营待办", businessType = OperationTypeConstants.UPDATE,
            operation = "完成运营待办")
    public CommonResult<WorkItemDetail> complete(@PathVariable("workItemNo") String workItemNo,
                                                 @Valid @RequestBody ActionRequest request) {
        return success(applicationService.complete(workItemNo, request));
    }

    /**
     * 追加不覆盖历史的待办处理备注。
     *
     * @param workItemNo 待办编号
     * @param request 带备注、请求幂等键和预期版本的操作命令
     * @return 更新后的待办详情
     */
    @PostMapping("/{workItemNo}/remark")
    @RequiresPermission("operations:work-item:remark")
    @OperationLog(moduleName = "统一运营待办", businessType = OperationTypeConstants.UPDATE,
            operation = "备注运营待办")
    public CommonResult<WorkItemDetail> remark(@PathVariable("workItemNo") String workItemNo,
                                               @Valid @RequestBody ActionRequest request) {
        return success(applicationService.remark(workItemNo, request));
    }

    /**
     * 导出当前操作人有权访问的待办。
     *
     * @param request 导出筛选条件
     * @param response HTTP 响应
     */
    @PostMapping("/export")
    @RequiresPermission("operations:work-item:export")
    @OperationLog(moduleName = "统一运营待办", businessType = OperationTypeConstants.EXPORT,
            operation = "导出运营待办")
    public void export(@RequestBody(required = false) SearchRequest request, HttpServletResponse response) {
        applicationService.export(request, response);
    }
}
