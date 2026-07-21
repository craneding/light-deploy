package com.lightdeploy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightdeploy.backend.entity.Server;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServerMapper extends BaseMapper<Server> {
}