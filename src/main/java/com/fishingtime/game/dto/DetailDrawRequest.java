package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 《细节》抽题请求：盲选一个题号
 */
@Data
public class DetailDrawRequest {

    /** 用户盲选的题号 1~6 */
    private Integer number;
}
