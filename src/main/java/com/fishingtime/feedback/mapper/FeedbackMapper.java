package com.fishingtime.feedback.mapper;

import com.fishingtime.feedback.domain.Feedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户反馈 Mapper
 */
@Mapper
public interface FeedbackMapper {

    void insert(Feedback feedback);
}
