package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 《细节》作答请求
 */
@Data
public class DetailAnswerRequest {

    /** 用户选择的选项键 A/B/C/D；超时客户端可不传（服务端按超时判错） */
    private String option;
}
