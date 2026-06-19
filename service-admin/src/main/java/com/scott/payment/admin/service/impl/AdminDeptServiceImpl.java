package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.converter.DeptConverter;
import com.scott.payment.admin.dto.SysDeptDTO;
import com.scott.payment.admin.service.AdminDeptService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysDeptDO;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台部门领域服务实现。
 */
@Service
public class AdminDeptServiceImpl implements AdminDeptService {

    private final SysDeptMapper sysDeptMapper;
    private final SysAppMapper sysAppMapper;

    /**
     * 创建后台部门领域服务。
     *
     * @param sysDeptMapper 部门 Mapper
     * @param sysAppMapper  应用 Mapper
     */
    public AdminDeptServiceImpl(SysDeptMapper sysDeptMapper, SysAppMapper sysAppMapper) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysAppMapper = sysAppMapper;
    }

    @Override
    public List<SysDeptDTO> tree() {
        List<SysDeptDO> departments = sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysDeptDO::getSortNo)
        );
        return DeptConverter.INSTANCE.buildTree(departments);
    }

    @Override
    public SysDeptDO getDept(Long id) {
        return sysDeptMapper.selectById(id);
    }

    @Override
    public List<SysDeptDO> exportDepts() {
        return sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysDeptDO::getSortNo)
        );
    }

    @Override
    public SysDeptDO createDept(SysDeptDO dept) {
        if (!StringUtils.hasText(dept.getDeptName())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "部门名称不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        dept.setId(null);
        dept.setAppId(getAdminAppId());
        dept.setCreatedAt(now);
        dept.setUpdatedAt(now);
        dept.setDeleted(AuthConstants.NOT_DELETED);
        if (dept.getStatus() == null) {
            dept.setStatus(1);
        }
        if (dept.getSortNo() == null) {
            dept.setSortNo(100);
        }
        if (dept.getParentId() == null) {
            dept.setParentId(0L);
        }
        sysDeptMapper.insert(dept);
        return dept;
    }

    @Override
    public SysDeptDO updateDept(Long id, SysDeptDO input) {
        SysDeptDO dept = sysDeptMapper.selectById(id);
        if (dept == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "部门不存在");
        }
        if (input.getParentId() != null) {
            dept.setParentId(input.getParentId());
        }
        if (input.getDeptName() != null) {
            dept.setDeptName(input.getDeptName());
        }
        if (input.getSortNo() != null) {
            dept.setSortNo(input.getSortNo());
        }
        if (input.getLeader() != null) {
            dept.setLeader(input.getLeader());
        }
        if (input.getPhone() != null) {
            dept.setPhone(input.getPhone());
        }
        if (input.getEmail() != null) {
            dept.setEmail(input.getEmail());
        }
        if (input.getStatus() != null) {
            dept.setStatus(input.getStatus());
        }
        dept.setUpdatedAt(LocalDateTime.now());
        sysDeptMapper.updateById(dept);
        return dept;
    }

    @Override
    public void removeDept(Long id) {
        SysDeptDO dept = sysDeptMapper.selectById(id);
        if (dept == null) {
            return;
        }
        dept.setDeleted(id);
        dept.setUpdatedAt(LocalDateTime.now());
        sysDeptMapper.updateById(dept);
    }

    /**
     * 查询后台管理应用主键，保证部门数据归属 admin 应用。
     *
     * @return admin 应用主键
     */
    private Long getAdminAppId() {
        SysAppDO app = sysAppMapper.selectOne(Wrappers.<SysAppDO>lambdaQuery()
                .eq(SysAppDO::getAppCode, AuthConstants.APP_ADMIN)
                .eq(SysAppDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (app == null) {
            throw new IllegalStateException("ADMIN app not found");
        }
        return app.getId();
    }
}
