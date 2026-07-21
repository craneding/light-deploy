package com.lightdeploy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightdeploy.backend.entity.DeployTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeployTaskMapper extends BaseMapper<DeployTask> {
}
