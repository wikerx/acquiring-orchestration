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
 * 后台岗位领域服务实现。
 */
@Service
public class AdminPostServiceImpl implements AdminPostService {

    private final SysPostMapper sysPostMapper;
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

    @Override
    public List<SysPostDO> listEnabledPosts() {
        return sysPostMapper.selectList(
                Wrappers.<SysPostDO>lambdaQuery()
                        .eq(SysPostDO::getStatus, 1)
                        .eq(SysPostDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysPostDO::getSortNo)
        );
    }

    @Override
    public SysPostDO getPost(Long id) {
        return sysPostMapper.selectById(id);
    }

    @Override
    public List<SysPostDO> exportPosts() {
        return sysPostMapper.selectList(
                Wrappers.<SysPostDO>lambdaQuery()
                        .eq(SysPostDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysPostDO::getSortNo)
        );
    }

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
