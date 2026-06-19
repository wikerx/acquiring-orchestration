package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminNoticeApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import static com.scott.payment.component.core.model.CommonResult.success;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.SysNoticeDO;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.*;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminNoticeController
 * @date : 2026-06-12 17:37
 * @email : scott_x@163.com
 * @description : 通知公告管理控制器，提供通知公告的增删改查分页接口
 * @status : create
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
    public CommonResult<PageResult<SysNoticeDO>> list(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "noticeTitle", required = false) String noticeTitle,
            @RequestParam(value = "noticeType", required = false) String noticeType,
            @RequestParam(value = "createBy", required = false) String createBy) {
        return success(adminNoticeApplicationService.pageNotices(pageNo, pageSize, noticeTitle, noticeType, createBy));
    }

    /**
     * 查询单条通知公告详情。
     *
     * @param id 通知公告主键 ID
     * @return 通知公告实体
     */
    @GetMapping("/{id}")
    @RequiresPermission("system:notice:list")
    public CommonResult<SysNoticeDO> detail(@PathVariable("id") Long id) {
        return success(adminNoticeApplicationService.getNotice(id));
    }

    /**
     * 新增通知公告。
     *
     * @param notice 通知公告实体（JSON 请求体）
     * @return 新增后的通知公告实体
     */
    @PostMapping
    @RequiresPermission("system:notice:add")
    @OperationLog(moduleName = "通知公告", businessType = OperationTypeConstants.CREATE, operation = "新增通知公告")
    public CommonResult<SysNoticeDO> create(@RequestBody SysNoticeDO notice) {
        return success(adminNoticeApplicationService.createNotice(notice));
    }

    /**
     * 修改通知公告。
     *
     * @param id     通知公告主键 ID
     * @param notice 通知公告实体（JSON 请求体，只更新非空字段）
     * @return 更新后的通知公告实体
     */
    @PutMapping("/{id}")
    @RequiresPermission("system:notice:edit")
    @OperationLog(moduleName = "通知公告", businessType = OperationTypeConstants.UPDATE, operation = "修改通知公告")
    public CommonResult<SysNoticeDO> update(@PathVariable("id") Long id, @RequestBody SysNoticeDO notice) {
        return success(adminNoticeApplicationService.updateNotice(id, notice));
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
}
