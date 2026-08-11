package com.fishingtime.feedback.service;

/**
 * 用户反馈服务接口
 */
public interface FeedbackService {

    /**
     * 提交反馈
     * @param userId 登录用户 ID（游客为 null）
     * @param content 反馈内容（已校验非空）
     */
    void submit(Long userId, String content);
}
