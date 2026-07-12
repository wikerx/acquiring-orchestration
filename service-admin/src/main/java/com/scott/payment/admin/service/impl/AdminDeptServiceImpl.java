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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDeptServiceImpl
 * @date : 2026-06-19 21:54
 * @email : scott_x@163.com
 * @description : 管理后台部门领域服务实现
 * @status : create
 *
 * <p>负责部门树组装、部门维护和 admin 应用归属校验等领域规则，不承担权限控制或页面交互逻辑。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDeptServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Dept Service Impl，位于 service-admin 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminDeptServiceImpl implements AdminDeptService {

    /**
     * 部门数据访问组件。
     */
    private final SysDeptMapper sysDeptMapper;

    /**
     * 应用数据访问组件。
     */
    private final SysAppMapper sysAppMapper;

    /**
     * 部门对象转换器。
     */
    private final DeptConverter deptConverter;

    /**
     * 创建后台部门领域服务。
     *
     * @param sysDeptMapper 部门 Mapper
     * @param sysAppMapper  应用 Mapper
     * @param deptConverter 部门对象转换器
     */
    public AdminDeptServiceImpl(SysDeptMapper sysDeptMapper,
                                SysAppMapper sysAppMapper,
                                DeptConverter deptConverter) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysAppMapper = sysAppMapper;
        this.deptConverter = deptConverter;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<SysDeptDTO> tree() {
        List<SysDeptDO> departments = sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysDeptDO::getSortNo)
        );
        return deptConverter.buildTree(departments);
    }

    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public SysDeptDO getDept(Long id) {
        return sysDeptMapper.selectById(id);
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<SysDeptDO> exportDepts() {
        return sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysDeptDO::getSortNo)
        );
    }

    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param dept 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param input 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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
