package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
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

    /**
     * 查询未删除部门并按排序号构建层级树。
     *
     * @return 后台管理应用的部门树
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<SysDeptDTO> tree() {
        List<SysDeptDO> departments = sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysDeptDO::getSortNo)
        );
        return deptConverter.buildTree(departments);
    }

    /**
     * 按主键查询部门资料。
     *
     * @param id 部门主键
     * @return 部门记录；不存在时返回 {@code null}
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public SysDeptDO getDept(Long id) {
        return sysDeptMapper.selectById(id);
    }

    /**
     * 查询全部未删除部门供导出，结果按排序号升序。
     *
     * @return 部门导出记录
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<SysDeptDO> exportDepts() {
        return sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysDeptDO::getSortNo)
        );
    }

    /**
     * 在后台管理应用下创建部门并补齐状态、排序号和根节点默认值。
     *
     * @param dept 待创建部门
     * @return 已写入主键和审计时间的部门记录
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
     * 局部更新指定部门的组织、负责人和状态字段。
     *
     * @param id 部门主键
     * @param input 非空字段覆盖请求
     * @return 更新后的部门记录
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
     * 逻辑删除指定部门；记录不存在时按幂等成功处理。
     *
     * @param id 部门主键
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
