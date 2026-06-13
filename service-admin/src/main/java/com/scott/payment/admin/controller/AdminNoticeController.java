package com.scott.payment.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import static com.scott.payment.component.core.model.CommonResult.success;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.SysNoticeDO;
import com.scott.payment.component.db.auth.mapper.SysNoticeMapper;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

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

    private final SysNoticeMapper sysNoticeMapper;

    public AdminNoticeController(SysNoticeMapper sysNoticeMapper) {
        this.sysNoticeMapper = sysNoticeMapper;
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

        LambdaQueryWrapper<SysNoticeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(noticeTitle), SysNoticeDO::getNoticeTitle, noticeTitle);
        wrapper.eq(StringUtils.hasText(noticeType), SysNoticeDO::getNoticeType, noticeType);
        wrapper.like(StringUtils.hasText(createBy), SysNoticeDO::getCreateBy, createBy);
        wrapper.eq(SysNoticeDO::getDeleted, AuthConstants.NOT_DELETED);
        wrapper.orderByDesc(SysNoticeDO::getCreatedAt);

        Page<SysNoticeDO> page = sysNoticeMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return success(PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords()));
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
        SysNoticeDO notice = sysNoticeMapper.selectById(id);
        return success(notice);
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
        notice.setId(null);
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        notice.setDeleted(0L);
        sysNoticeMapper.insert(notice);
        return success(notice);
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
        notice.setId(id);
        notice.setUpdatedAt(LocalDateTime.now());
        sysNoticeMapper.updateById(notice);
        return success(notice);
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
        SysNoticeDO notice = sysNoticeMapper.selectById(id);
        if (notice != null) {
            notice.setDeleted(id);
            notice.setUpdatedAt(LocalDateTime.now());
            sysNoticeMapper.updateById(notice);
        }
        return success(null);
    }
}
