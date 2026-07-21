package com.lightdeploy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightdeploy.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
