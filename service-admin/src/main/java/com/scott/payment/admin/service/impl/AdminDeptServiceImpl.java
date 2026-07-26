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

    @Override
    /**
     * 完成 tree 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public List<SysDeptDTO> tree() {
        List<SysDeptDO> departments = sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysDeptDO::getSortNo)
        );
        return deptConverter.buildTree(departments);
    }

    @Override
    /**
     * 完成 get Dept 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public SysDeptDO getDept(Long id) {
        return sysDeptMapper.selectById(id);
    }

    @Override
    /**
     * 完成 export Depts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public List<SysDeptDO> exportDepts() {
        return sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysDeptDO::getSortNo)
        );
    }

    @Override
    /**
     * 完成 create Dept 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param dept dept 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 写入或更新 update Dept 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param input input 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 remove Dept 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
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
