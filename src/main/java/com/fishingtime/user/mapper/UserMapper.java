package com.fishingtime.user.mapper;

import com.fishingtime.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper — SQL 全部在 XML 中定义
 */
@Mapper
public interface UserMapper {

    void insertUser(User user);

    User selectByUsername(String username);

    User selectByOpenidAndAppId(@Param("openid") String openid, @Param("wxAppid") String wxAppid);

    /** 兼容历史数据：旧记录还没有 wx_appid 时，按 openid 找到后在首次登录时补齐。 */
    User selectLegacyByOpenid(String openid);

    int bindWxAppid(@Param("id") Long id, @Param("wxAppid") String wxAppid);

    User selectById(Long id);

    void updateProfile(User user);

    /** 当前最大的游客序号（昵称「人民xxxxx」的数字部分，无则 0） */
    Long selectMaxGuestNo();
}
