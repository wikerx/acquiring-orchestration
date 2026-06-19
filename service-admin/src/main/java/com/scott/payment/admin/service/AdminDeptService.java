package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysDeptDTO;
import com.scott.payment.component.db.auth.entity.SysDeptDO;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDeptService
 * @date : 2026-06-19 21:52
 * @email : scott_x@163.com
 * @description : 管理后台部门领域服务
 * @status : create
 *
 * <p>负责部门树查询、部门维护和 admin 应用归属校验等领域规则，不处理控制器协议适配逻辑。</p>
 */
public interface AdminDeptService {

    /**
     * 查询部门树。
     *
     * @return 树形部门列表
     */
    List<SysDeptDTO> tree();

    /**
     * 查询部门详情。
     *
     * @param id 部门主键
     * @return 部门详情
     */
    SysDeptDO getDept(Long id);

    /**
     * 导出全部部门资料。
     *
     * @return 部门列表
     */
    List<SysDeptDO> exportDepts();

    /**
     * 新增部门。
     *
     * @param dept 部门实体
     * @return 保存后的部门
     */
    SysDeptDO createDept(SysDeptDO dept);

    /**
     * 更新部门。
     *
     * @param id    部门主键
     * @param input 更新输入
     * @return 更新后的部门
     */
    SysDeptDO updateDept(Long id, SysDeptDO input);

    /**
     * 逻辑删除部门。
     *
     * @param id 部门主键
     */
    void removeDept(Long id);
}
