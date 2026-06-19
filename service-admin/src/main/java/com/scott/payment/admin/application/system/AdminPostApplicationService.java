package com.scott.payment.admin.application.system;

import com.scott.payment.admin.service.AdminPostService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.SysPostDO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPostApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台岗位管理应用服务
 * @status : create
 */
@Service
public class AdminPostApplicationService {

    private final AdminPostService adminPostService;

    /**
     * 创建后台岗位应用服务。
     *
     * @param adminPostService 岗位领域服务
     */
    public AdminPostApplicationService(AdminPostService adminPostService) {
        this.adminPostService = adminPostService;
    }

    /**
     * 分页查询岗位列表。
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @param postCode 岗位编码
     * @param postName 岗位名称
     * @param status   状态
     * @return 岗位分页结果
     */
    public PageResult<SysPostDO> pagePosts(int pageNo, int pageSize, String postCode, String postName, Integer status) {
        return adminPostService.pagePosts(pageNo, pageSize, postCode, postName, status);
    }

    /**
     * 查询全部启用岗位。
     *
     * @return 启用岗位列表
     */
    public List<SysPostDO> listEnabledPosts() {
        return adminPostService.listEnabledPosts();
    }

    /**
     * 查询岗位详情。
     *
     * @param id 主键
     * @return 岗位详情
     */
    public SysPostDO getPost(Long id) {
        return adminPostService.getPost(id);
    }

    /**
     * 导出岗位列表。
     *
     * @return 岗位列表
     */
    public List<SysPostDO> exportPosts() {
        return adminPostService.exportPosts();
    }

    /**
     * 新增岗位。
     *
     * @param post 岗位实体
     * @return 保存后的岗位
     */
    public SysPostDO createPost(SysPostDO post) {
        return adminPostService.createPost(post);
    }

    /**
     * 更新岗位。
     *
     * @param id    主键
     * @param input 更新输入
     * @return 更新后的岗位
     */
    public SysPostDO updatePost(Long id, SysPostDO input) {
        return adminPostService.updatePost(id, input);
    }

    /**
     * 逻辑删除岗位。
     *
     * @param id 主键
     */
    public void removePost(Long id) {
        adminPostService.removePost(id);
    }
}
