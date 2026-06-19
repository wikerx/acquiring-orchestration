package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysDeptDTO;
import com.scott.payment.admin.service.AdminDeptService;
import com.scott.payment.component.db.auth.entity.SysDeptDO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDeptApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台部门管理应用服务
 * @status : create
 */
@Service
public class AdminDeptApplicationService {

    private final AdminDeptService adminDeptService;

    /**
     * 创建后台部门应用服务。
     *
     * @param adminDeptService 部门领域服务
     */
    public AdminDeptApplicationService(AdminDeptService adminDeptService) {
        this.adminDeptService = adminDeptService;
    }

    /**
     * 查询部门树。
     *
     * @return 树形部门列表
     */
    public List<SysDeptDTO> tree() {
        return adminDeptService.tree();
    }

    /**
     * 查询部门详情。
     *
     * @param id 部门主键
     * @return 部门详情
     */
    public SysDeptDO getDept(Long id) {
        return adminDeptService.getDept(id);
    }

    /**
     * 导出全部部门资料。
     *
     * @return 部门列表
     */
    public List<SysDeptDO> exportDepts() {
        return adminDeptService.exportDepts();
    }

    /**
     * 新增部门。
     *
     * @param dept 部门实体
     * @return 保存后的部门
     */
    public SysDeptDO createDept(SysDeptDO dept) {
        return adminDeptService.createDept(dept);
    }

    /**
     * 更新部门。
     *
     * @param id    部门主键
     * @param input 更新输入
     * @return 更新后的部门
     */
    public SysDeptDO updateDept(Long id, SysDeptDO input) {
        return adminDeptService.updateDept(id, input);
    }

    /**
     * 逻辑删除部门。
     *
     * @param id 部门主键
     */
    public void removeDept(Long id) {
        adminDeptService.removeDept(id);
    }
}
