package com.scott.payment.admin.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.SysPostDO;

import java.util.List;

/**
 * 后台岗位领域服务。
 *
 * <p>负责岗位查询、维护和 admin 应用归属校验等领域规则。</p>
 */
public interface AdminPostService {

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
    PageResult<SysPostDO> pagePosts(int pageNo, int pageSize, String postCode, String postName, Integer status);

    /**
     * 查询全部启用岗位。
     *
     * @return 启用岗位列表
     */
    List<SysPostDO> listEnabledPosts();

    /**
     * 查询岗位详情。
     *
     * @param id 主键
     * @return 岗位详情
     */
    SysPostDO getPost(Long id);

    /**
     * 导出岗位列表。
     *
     * @return 岗位列表
     */
    List<SysPostDO> exportPosts();

    /**
     * 新增岗位。
     *
     * @param post 岗位实体
     * @return 保存后的岗位
     */
    SysPostDO createPost(SysPostDO post);

    /**
     * 更新岗位。
     *
     * @param id    主键
     * @param input 更新输入
     * @return 更新后的岗位
     */
    SysPostDO updatePost(Long id, SysPostDO input);

    /**
     * 逻辑删除岗位。
     *
     * @param id 主键
     */
    void removePost(Long id);
}
