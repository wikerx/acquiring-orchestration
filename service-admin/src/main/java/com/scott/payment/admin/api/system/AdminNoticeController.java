package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminNoticeApplicationService;
import com.scott.payment.admin.dto.system.NoticeDTOs.BatchDeleteRequest;
import com.scott.payment.admin.dto.system.NoticeDTOs.Response;
import com.scott.payment.admin.dto.system.NoticeDTOs.SaveRequest;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台通知公告控制器。
 *
 * <p>负责公告查询和维护请求的协议适配，业务编排由应用服务完成。</p>
 */
@RestController
@RequestMapping("/admin/system/notice")
public class AdminNoticeController {

    private final AdminNoticeApplicationService adminNoticeApplicationService;

    /**
     * 创建通知公告控制器。
     *
     * @param adminNoticeApplicationService 通知公告应用服务
     */
    public AdminNoticeController(AdminNoticeApplicationService adminNoticeApplicationService) {
        this.adminNoticeApplicationService = adminNoticeApplicationService;
    }

    /**
     * 分页查询通知公告列表。
     *
     * <p>支持按通知标题、通知类型、创建人模糊筛选。
     *
     * @param pageNo      页码，默认 1
     * @param pageSize    每页条数，默认 10
     * @param noticeTitle 通知标题（可选）
     * @param noticeType  通知类型（可选）
     * @param createBy    创建人（可选）
     * @return 分页的通知公告列表
     */
    @GetMapping("/list")
    @RequiresPermission("system:notice:list")
    public CommonResult<PageResult<Response>> list(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "noticeTitle", required = false) String noticeTitle,
            @RequestParam(value = "noticeType", required = false) String noticeType,
            @RequestParam(value = "createBy", required = false) String createBy) {
        return success(adminNoticeApplicationService.pageNotices(pageNo, pageSize, noticeTitle, noticeType, createBy));
    }

    /**
     * 查询工作台展示的启用公告。
     *
     * @param limit 最大条数
     * @return 启用公告列表
     */
    @GetMapping("/latest")
    @RequiresPermission("dashboard:view")
    public CommonResult<List<Response>> latest(@RequestParam(value = "limit", defaultValue = "3") int limit) {
        return success(adminNoticeApplicationService.listDashboardNotices(limit));
    }

    /**
     * 查询单条通知公告详情。
     *
     * @param id 通知公告主键 ID
     * @return 通知公告详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("system:notice:list")
    public CommonResult<Response> detail(@PathVariable("id") Long id) {
        return success(adminNoticeApplicationService.getNotice(id));
    }

    /**
     * 新增通知公告。
     *
     * @param request 通知公告保存请求
     * @return 新增后的通知公告
     */
    @PostMapping
    @RequiresPermission("system:notice:add")
    @OperationLog(moduleName = "通知公告", businessType = OperationTypeConstants.CREATE, operation = "新增通知公告")
    public CommonResult<Response> create(@Valid @RequestBody SaveRequest request) {
        return success(adminNoticeApplicationService.createNotice(request));
    }

    /**
     * 修改通知公告。
     *
     * @param id 通知公告主键 ID
     * @param request 通知公告保存请求
     * @return 更新后的通知公告
     */
    @PutMapping("/{id}")
    @RequiresPermission("system:notice:edit")
    @OperationLog(moduleName = "通知公告", businessType = OperationTypeConstants.UPDATE, operation = "修改通知公告")
    public CommonResult<Response> update(@PathVariable("id") Long id,
                                         @Valid @RequestBody SaveRequest request) {
        return success(adminNoticeApplicationService.updateNotice(id, request));
    }

    /**
     * 删除通知公告（逻辑删除）。
     *
     * @param id 通知公告主键 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("system:notice:remove")
    @OperationLog(moduleName = "通知公告", businessType = OperationTypeConstants.DELETE, operation = "删除通知公告")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        adminNoticeApplicationService.removeNotice(id);
        return success(null);
    }

    /**
     * 批量删除通知公告（逻辑删除）。
     *
     * @param request 批量删除请求
     * @return 空响应
     */
    @DeleteMapping("/batch")
    @RequiresPermission("system:notice:remove")
    @OperationLog(moduleName = "通知公告", businessType = OperationTypeConstants.DELETE, operation = "批量删除通知公告")
    public CommonResult<Void> removeBatch(@Valid @RequestBody BatchDeleteRequest request) {
        adminNoticeApplicationService.removeNotices(request == null ? Collections.emptyList() : request.ids());
        return success(null);
    }
}
