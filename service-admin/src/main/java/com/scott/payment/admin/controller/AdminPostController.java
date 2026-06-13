package com.scott.payment.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import static com.scott.payment.component.core.model.CommonResult.success;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysPostDO;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysPostMapper;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPostController
 * @date : 2026-06-12 20:00
 * @email : scott_x@163.com
 * @description : 岗位管理控制器，提供岗位增删改查
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/post")
public class AdminPostController {

    private final SysPostMapper sysPostMapper;
    private final SysAppMapper sysAppMapper;

    public AdminPostController(SysPostMapper sysPostMapper, SysAppMapper sysAppMapper) {
        this.sysPostMapper = sysPostMapper;
        this.sysAppMapper = sysAppMapper;
    }

    /**
     * 分页查询岗位列表。
     *
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @param postCode 岗位编码（可选）
     * @param postName 岗位名称（可选）
     * @param status   状态（可选）
     * @return 分页岗位列表
     */
    @GetMapping("/list")
    @RequiresPermission("system:post:list")
    public CommonResult<PageResult<SysPostDO>> list(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                  @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                  @RequestParam(value = "postCode", required = false) String postCode,
                                                  @RequestParam(value = "postName", required = false) String postName,
                                                  @RequestParam(value = "status", required = false) Integer status) {
        LambdaQueryWrapper<SysPostDO> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(postCode), SysPostDO::getPostCode, postCode);
        w.like(StringUtils.hasText(postName), SysPostDO::getPostName, postName);
        w.eq(status != null, SysPostDO::getStatus, status);
        w.eq(SysPostDO::getDeleted, AuthConstants.NOT_DELETED);
        w.orderByAsc(SysPostDO::getSortNo);
        Page<SysPostDO> page = sysPostMapper.selectPage(new Page<>(pageNo, pageSize), w);
        return success(PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords()));
    }

    /**
     * 查询全部岗位（用于下拉选择）。
     *
     * @return 全部启用岗位
     */
    @GetMapping("/all")
    @RequiresPermission("system:post:list")
    public CommonResult<List<SysPostDO>> all() {
        return success(sysPostMapper.selectList(
                new LambdaQueryWrapper<SysPostDO>().eq(SysPostDO::getStatus, 1).eq(SysPostDO::getDeleted, AuthConstants.NOT_DELETED).orderByAsc(SysPostDO::getSortNo)));
    }

    /**
     * 查询岗位详情。
     *
     * @param id 岗位主键 ID
     * @return 岗位实体
     */
    @GetMapping("/{id}")
    @RequiresPermission("system:post:query")
    public CommonResult<SysPostDO> detail(@PathVariable("id") Long id) {
        return success(sysPostMapper.selectById(id));
    }

    /**
     * 导出岗位列表。
     *
     * @return 岗位列表
     */
    @GetMapping("/export")
    @RequiresPermission("system:post:export")
    @OperationLog(moduleName = "岗位管理", businessType = OperationTypeConstants.EXPORT, operation = "导出岗位")
    public CommonResult<List<SysPostDO>> export() {
        return success(sysPostMapper.selectList(
                new LambdaQueryWrapper<SysPostDO>()
                        .eq(SysPostDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysPostDO::getSortNo)
        ));
    }

    /**
     * 新增岗位。
     *
     * @param post 岗位实体
     * @return 新增后的岗位
     */
    @PostMapping
    @RequiresPermission("system:post:add")
    @OperationLog(moduleName = "岗位管理", businessType = OperationTypeConstants.CREATE, operation = "新增岗位")
    public CommonResult<SysPostDO> create(@RequestBody SysPostDO post) {
        if (!StringUtils.hasText(post.getPostCode())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "岗位编码不能为空");
        }
        if (!StringUtils.hasText(post.getPostName())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "岗位名称不能为空");
        }
        post.setId(null);
        post.setAppId(getAdminAppId());
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        post.setDeleted(0L);
        if (post.getStatus() == null) post.setStatus(1);
        if (post.getSortNo() == null) post.setSortNo(100);
        sysPostMapper.insert(post);
        return success(post);
    }

    /**
     * 修改岗位。
     *
     * @param id   岗位主键 ID
     * @param post 岗位实体
     * @return 更新后的岗位
     */
    @PutMapping("/{id}")
    @RequiresPermission("system:post:edit")
    @OperationLog(moduleName = "岗位管理", businessType = OperationTypeConstants.UPDATE, operation = "修改岗位")
    public CommonResult<SysPostDO> update(@PathVariable("id") Long id, @RequestBody SysPostDO input) {
        SysPostDO post = sysPostMapper.selectById(id);
        if (post == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "岗位不存在");
        }
        if (input.getPostCode() != null) post.setPostCode(input.getPostCode());
        if (input.getPostName() != null) post.setPostName(input.getPostName());
        if (input.getSortNo() != null) post.setSortNo(input.getSortNo());
        if (input.getStatus() != null) post.setStatus(input.getStatus());
        if (input.getRemark() != null) post.setRemark(input.getRemark());
        post.setUpdatedAt(LocalDateTime.now());
        sysPostMapper.updateById(post);
        return success(post);
    }

    /**
     * 删除岗位（逻辑删除）。
     *
     * @param id 岗位主键 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("system:post:remove")
    @OperationLog(moduleName = "岗位管理", businessType = OperationTypeConstants.DELETE, operation = "删除岗位")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        SysPostDO post = sysPostMapper.selectById(id);
        if (post != null) {
            post.setDeleted(id);
            post.setUpdatedAt(LocalDateTime.now());
            sysPostMapper.updateById(post);
        }
        return success(null);
    }

    private Long getAdminAppId() {
        SysAppDO app = sysAppMapper.selectOne(
                Wrappers.<SysAppDO>lambdaQuery()
                        .eq(SysAppDO::getAppCode, AuthConstants.APP_ADMIN)
                        .eq(SysAppDO::getDeleted, AuthConstants.NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (app == null) {
            throw new IllegalStateException("ADMIN app not found");
        }
        return app.getId();
    }

}
