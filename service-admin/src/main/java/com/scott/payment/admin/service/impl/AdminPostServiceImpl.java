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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPostServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Post Service Impl，位于 service-admin 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param pageNo 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param pageSize 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param postCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param postName 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
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
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public SysPostDO getPost(Long id) {
        return sysPostMapper.selectById(id);
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param post 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param input 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
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
