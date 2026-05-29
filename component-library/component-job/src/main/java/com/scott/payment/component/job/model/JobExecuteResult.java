package com.scott.payment.component.job.model;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecuteResult
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 任务执行结果模型
 * @status : create
 */
@Data
public class JobExecuteResult {

    /**
     * 任务是否执行成功，true 表示调度任务完成，false 表示任务执行失败或被业务拒绝。
     */
    private boolean success;

    /**
     * 任务执行结果说明，成功时可为 success，失败时记录可读错误原因。
     */
    private String message;

    public static JobExecuteResult success() {
        JobExecuteResult result = new JobExecuteResult();
        result.setSuccess(true);
        result.setMessage("success");
        return result;
    }
}
