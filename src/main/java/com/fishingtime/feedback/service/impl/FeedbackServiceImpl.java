package com.fishingtime.feedback.service.impl;

import com.fishingtime.feedback.domain.Feedback;
import com.fishingtime.feedback.mapper.FeedbackMapper;
import com.fishingtime.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户反馈服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, String content) {
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setContent(content);
        feedbackMapper.insert(feedback);
        log.info("[反馈] userId={} 提交反馈，长度={}", userId, content.length());
    }
}
