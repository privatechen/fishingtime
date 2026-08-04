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

    User selectById(Long id);

    void updateProfile(User user);
}
