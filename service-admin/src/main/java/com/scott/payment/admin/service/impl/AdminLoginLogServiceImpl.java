package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.converter.LoginLogConverter;
import com.scott.payment.admin.dto.SysLoginLogDTO;
import com.scott.payment.admin.dto.SysLoginLogQueryRequest;
import com.scott.payment.admin.service.AdminLoginLogService;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.SysLoginLogDO;
import com.scott.payment.component.db.auth.mapper.SysLoginLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminLoginLogServiceImpl
 * @date : 2026-06-19 21:55
 * @email : scott_x@163.com
 * @description : 管理后台登录日志领域服务实现
 * @status : create
 *
 * <p>负责后台登录日志查询过滤与分页组装，不承担权限控制、控制器协议适配和页面展示逻辑。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminLoginLogServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Login Log Service Impl，位于 service-admin 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminLoginLogServiceImpl implements AdminLoginLogService {

    /**
     * 登录日志 Mapper。
     */
    private final SysLoginLogMapper loginLogMapper;

    /**
     * 登录日志对象转换器。
     */
    private final LoginLogConverter loginLogConverter;

    /**
     * 创建登录日志服务实现。
     *
     * @param loginLogMapper 登录日志 Mapper
     * @param loginLogConverter 登录日志对象转换器
     */
    public AdminLoginLogServiceImpl(SysLoginLogMapper loginLogMapper, LoginLogConverter loginLogConverter) {
        this.loginLogMapper = loginLogMapper;
        this.loginLogConverter = loginLogConverter;
    }

    /**
     * 按条件查询登录日志。
     *
     * @param request 查询条件
     * @return 登录日志分页结果
     */
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public PageResult<SysLoginLogDTO> pageLoginLogs(SysLoginLogQueryRequest request) {
        SysLoginLogQueryRequest query = request == null ? new SysLoginLogQueryRequest() : request;
        Page<SysLoginLogDO> page = loginLogMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildLoginLogQueryWrapper(query)
        );
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream().map(loginLogConverter::toDTO).toList()
        );
    }

    /**
     * 构建登录日志查询条件。
     *
     * @param query 查询请求
     * @return MyBatis Plus 查询条件
     */
    private LambdaQueryWrapper<SysLoginLogDO> buildLoginLogQueryWrapper(SysLoginLogQueryRequest query) {
        return Wrappers.<SysLoginLogDO>lambdaQuery()
                .eq(query.getAppId() != null, SysLoginLogDO::getAppId, query.getAppId())
                .eq(StringUtils.hasText(query.getLoginIp()), SysLoginLogDO::getLoginIp, query.getLoginIp())
                .eq(StringUtils.hasText(query.getMerchantId()), SysLoginLogDO::getMerchantId, query.getMerchantId())
                .eq(query.getLoginStatus() != null, SysLoginLogDO::getLoginStatus, query.getLoginStatus())
                .likeRight(StringUtils.hasText(query.getLoginAccount()), SysLoginLogDO::getLoginAccount, query.getLoginAccount())
                .ge(query.getLoginStartAt() != null, SysLoginLogDO::getLoginAt, query.getLoginStartAt())
                .le(query.getLoginEndAt() != null, SysLoginLogDO::getLoginAt, query.getLoginEndAt())
                .orderByDesc(SysLoginLogDO::getLoginAt);
    }

}
