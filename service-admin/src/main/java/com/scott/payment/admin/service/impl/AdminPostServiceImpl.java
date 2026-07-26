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

    @Override
    /**
     * 完成 page Posts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param pageNo page No 输入值，含义由调用方法名称和所属业务对象限定
     * @param pageSize page Size 输入值，含义由调用方法名称和所属业务对象限定
     * @param postCode post Code 输入值，含义由调用方法名称和所属业务对象限定
     * @param postName post Name 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 list Enabled Posts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public List<SysPostDO> listEnabledPosts() {
        return sysPostMapper.selectList(
                Wrappers.<SysPostDO>lambdaQuery()
                        .eq(SysPostDO::getStatus, 1)
                        .eq(SysPostDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysPostDO::getSortNo)
        );
    }

    @Override
    /**
     * 完成 get Post 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public SysPostDO getPost(Long id) {
        return sysPostMapper.selectById(id);
    }

    @Override
    /**
     * 完成 export Posts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public List<SysPostDO> exportPosts() {
        return sysPostMapper.selectList(
                Wrappers.<SysPostDO>lambdaQuery()
                        .eq(SysPostDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysPostDO::getSortNo)
        );
    }

    @Override
    /**
     * 完成 create Post 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param post post 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 写入或更新 update Post 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param input input 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 remove Post 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
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
