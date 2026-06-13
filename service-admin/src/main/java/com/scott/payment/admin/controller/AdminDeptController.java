package com.scott.payment.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.converter.DeptConverter;
import com.scott.payment.admin.dto.SysDeptDTO;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import static com.scott.payment.component.core.model.CommonResult.success;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysDeptDO;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysDeptMapper;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDeptController
 * @date : 2026-06-12 20:00
 * @email : scott_x@163.com
 * @description : 部门管理控制器，提供部门增删改查与树形查询
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/dept")
public class AdminDeptController {

    private final SysDeptMapper sysDeptMapper;
    private final SysAppMapper sysAppMapper;

    public AdminDeptController(SysDeptMapper sysDeptMapper, SysAppMapper sysAppMapper) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysAppMapper = sysAppMapper;
    }

    /**
     * 查询部门树。
     *
     * @return 部门树形列表
     */
    @GetMapping("/tree")
    @RequiresPermission("system:dept:list")
    public CommonResult<List<SysDeptDTO>> tree() {
        List<SysDeptDO> all = sysDeptMapper.selectList(
                new LambdaQueryWrapper<SysDeptDO>().eq(SysDeptDO::getDeleted, AuthConstants.NOT_DELETED).orderByAsc(SysDeptDO::getSortNo));
        return success(DeptConverter.INSTANCE.buildTree(all));
    }

    /**
     * 查询部门详情。
     *
     * @param id 部门主键 ID
     * @return 部门实体
     */
    @GetMapping("/{id}")
    @RequiresPermission("system:dept:query")
    public CommonResult<SysDeptDO> detail(@PathVariable("id") Long id) {
        return success(sysDeptMapper.selectById(id));
    }

    /**
     * 导出部门树。
     *
     * @return 部门列表
     */
    @GetMapping("/export")
    @RequiresPermission("system:dept:export")
    @OperationLog(moduleName = "部门管理", businessType = OperationTypeConstants.EXPORT, operation = "导出部门")
    public CommonResult<List<SysDeptDO>> export() {
        return success(sysDeptMapper.selectList(
                new LambdaQueryWrapper<SysDeptDO>()
                        .eq(SysDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysDeptDO::getSortNo)
        ));
    }

    /**
     * 新增部门。
     *
     * @param dept 部门实体
     * @return 新增后的部门
     */
    @PostMapping
    @RequiresPermission("system:dept:add")
    @OperationLog(moduleName = "部门管理", businessType = OperationTypeConstants.CREATE, operation = "新增部门")
    public CommonResult<SysDeptDO> create(@RequestBody SysDeptDO dept) {
        if (!StringUtils.hasText(dept.getDeptName())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "部门名称不能为空");
        }
        dept.setId(null);
        dept.setAppId(getAdminAppId());
        dept.setCreatedAt(LocalDateTime.now());
        dept.setUpdatedAt(LocalDateTime.now());
        dept.setDeleted(0L);
        if (dept.getStatus() == null) dept.setStatus(1);
        if (dept.getSortNo() == null) dept.setSortNo(100);
        if (dept.getParentId() == null) dept.setParentId(0L);
        sysDeptMapper.insert(dept);
        return success(dept);
    }

    /**
     * 修改部门。
     *
     * @param id   部门主键 ID
     * @param input 部门实体
     * @return 更新后的部门
     */
    @PutMapping("/{id}")
    @RequiresPermission("system:dept:edit")
    @OperationLog(moduleName = "部门管理", businessType = OperationTypeConstants.UPDATE, operation = "修改部门")
    public CommonResult<SysDeptDO> update(@PathVariable("id") Long id, @RequestBody SysDeptDO input) {
        SysDeptDO dept = sysDeptMapper.selectById(id);
        if (dept == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "部门不存在");
        }
        if (input.getParentId() != null) dept.setParentId(input.getParentId());
        if (input.getDeptName() != null) dept.setDeptName(input.getDeptName());
        if (input.getSortNo() != null) dept.setSortNo(input.getSortNo());
        if (input.getLeader() != null) dept.setLeader(input.getLeader());
        if (input.getPhone() != null) dept.setPhone(input.getPhone());
        if (input.getEmail() != null) dept.setEmail(input.getEmail());
        if (input.getStatus() != null) dept.setStatus(input.getStatus());
        dept.setUpdatedAt(LocalDateTime.now());
        sysDeptMapper.updateById(dept);
        return success(dept);
    }

    /**
     * 删除部门（逻辑删除）。
     *
     * @param id 部门主键 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("system:dept:remove")
    @OperationLog(moduleName = "部门管理", businessType = OperationTypeConstants.DELETE, operation = "删除部门")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        SysDeptDO dept = sysDeptMapper.selectById(id);
        if (dept != null) {
            dept.setDeleted(id);
            dept.setUpdatedAt(LocalDateTime.now());
            sysDeptMapper.updateById(dept);
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
