package com.sinopay.payment.component.job.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecuteResult
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 任务执行结果模型
 * @status : create
 */
public class JobExecuteResult {

    private boolean success;
    private String message;

    public static JobExecuteResult success() {
        JobExecuteResult result = new JobExecuteResult();
        result.setSuccess(true);
        result.setMessage("success");
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

