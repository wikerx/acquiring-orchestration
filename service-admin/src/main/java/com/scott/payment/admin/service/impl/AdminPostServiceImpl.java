package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.service.AdminPostService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysPostDO;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysPostMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPostServiceImpl
 * @date : 2026-06-19 21:56
 * @email : scott_x@163.com
 * @description : 管理后台岗位领域服务实现
 * @status : create
 *
 * <p>负责岗位查询、维护和 admin 应用归属校验等领域规则，不承担权限控制或页面交互逻辑。</p>
 */
@Service
public class AdminPostServiceImpl implements AdminPostService {

    /**
     * 岗位数据访问组件。
     */
    private final SysPostMapper sysPostMapper;

    /**
     * 应用数据访问组件。
     */
    private final SysAppMapper sysAppMapper;

    /**
     * 创建后台岗位领域服务。
     *
     * @param sysPostMapper 岗位 Mapper
     * @param sysAppMapper  应用 Mapper
     */
    public AdminPostServiceImpl(SysPostMapper sysPostMapper, SysAppMapper sysAppMapper) {
        this.sysPostMapper = sysPostMapper;
        this.sysAppMapper = sysAppMapper;
    }

    /**
     * 分页查询未删除岗位。
     *
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @param postCode 岗位编码模糊条件
     * @param postName 岗位名称模糊条件
     * @param status 启停状态
     * @return 按排序号升序的岗位分页结果
     */
    @Override
    public PageResult<SysPostDO> pagePosts(int pageNo, int pageSize, String postCode, String postName, Integer status) {
        LambdaQueryWrapper<SysPostDO> queryWrapper = Wrappers.<SysPostDO>lambdaQuery()
                .like(StringUtils.hasText(postCode), SysPostDO::getPostCode, postCode)
                .like(StringUtils.hasText(postName), SysPostDO::getPostName, postName)
                .eq(status != null, SysPostDO::getStatus, status)
                .eq(SysPostDO::getDeleted, AuthConstants.NOT_DELETED)
                .orderByAsc(SysPostDO::getSortNo);
        Page<SysPostDO> page = sysPostMapper.selectPage(new Page<>(pageNo, pageSize), queryWrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 查询全部启用且未删除的岗位供用户分配。
     *
     * @return 按排序号升序的岗位列表
     */
    @Override
    public List<SysPostDO> listEnabledPosts() {
        return sysPostMapper.selectList(
                Wrappers.<SysPostDO>lambdaQuery()
                        .eq(SysPostDO::getStatus, 1)
                        .eq(SysPostDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysPostDO::getSortNo)
        );
    }

    /**
     * 按主键查询岗位资料。
     *
     * @param id 岗位主键
     * @return 岗位记录；不存在时返回 {@code null}
     */
    @Override
    public SysPostDO getPost(Long id) {
        return sysPostMapper.selectById(id);
    }

    /**
     * 查询全部未删除岗位供导出。
     *
     * @return 按排序号升序的岗位导出记录
     */
    @Override
    public List<SysPostDO> exportPosts() {
        return sysPostMapper.selectList(
                Wrappers.<SysPostDO>lambdaQuery()
                        .eq(SysPostDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysPostDO::getSortNo)
        );
    }

    /**
     * 在后台管理应用下创建岗位并补齐默认状态和排序号。
     *
     * @param post 待创建岗位
     * @return 已写入主键和审计时间的岗位记录
     */
    @Override
    public SysPostDO createPost(SysPostDO post) {
        if (!StringUtils.hasText(post.getPostCode())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "岗位编码不能为空");
        }
        if (!StringUtils.hasText(post.getPostName())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "岗位名称不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        post.setId(null);
        post.setAppId(getAdminAppId());
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        post.setDeleted(AuthConstants.NOT_DELETED);
        if (post.getStatus() == null) {
            post.setStatus(1);
        }
        if (post.getSortNo() == null) {
            post.setSortNo(100);
        }
        sysPostMapper.insert(post);
        return post;
    }

    /**
     * 局部更新指定岗位的编码、名称、状态和备注。
     *
     * @param id 岗位主键
     * @param input 非空字段覆盖请求
     * @return 更新后的岗位记录
     */
    @Override
    public SysPostDO updatePost(Long id, SysPostDO input) {
        SysPostDO post = sysPostMapper.selectById(id);
        if (post == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "岗位不存在");
        }
        if (input.getPostCode() != null) {
            post.setPostCode(input.getPostCode());
        }
        if (input.getPostName() != null) {
            post.setPostName(input.getPostName());
        }
        if (input.getSortNo() != null) {
            post.setSortNo(input.getSortNo());
        }
        if (input.getStatus() != null) {
            post.setStatus(input.getStatus());
        }
        if (input.getRemark() != null) {
            post.setRemark(input.getRemark());
        }
        post.setUpdatedAt(LocalDateTime.now());
        sysPostMapper.updateById(post);
        return post;
    }

    /**
     * 逻辑删除指定岗位；记录不存在时按幂等成功处理。
     *
     * @param id 岗位主键
     */
    @Override
    public void removePost(Long id) {
        SysPostDO post = sysPostMapper.selectById(id);
        if (post == null) {
            return;
        }
        post.setDeleted(id);
        post.setUpdatedAt(LocalDateTime.now());
        sysPostMapper.updateById(post);
    }

    /**
     * 查询后台管理应用主键，保证岗位数据归属 admin 应用。
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
