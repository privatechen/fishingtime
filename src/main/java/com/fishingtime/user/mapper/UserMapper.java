package com.fishingtime.user.mapper;

import com.fishingtime.user.domain.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper — SQL 全部在 XML 中定义
 */
@Mapper
public interface UserMapper {

    void insertUser(User user);

    User selectByUsername(String username);

    User selectByOpenid(String openid);

    User selectById(Long id);

    void updateProfile(User user);

    /** 当前最大的游客序号（昵称「人民xxxxx」的数字部分，无则 0） */
    Long selectMaxGuestNo();
}
