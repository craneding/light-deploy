package com.lightdeploy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightdeploy.backend.entity.DeployProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeployProfileMapper extends BaseMapper<DeployProfile> {
}