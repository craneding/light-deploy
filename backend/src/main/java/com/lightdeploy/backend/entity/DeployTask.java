package com.lightdeploy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("deploy_tasks")
public class DeployTask {
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    private String name;
    private Integer projectId;
    private Integer profileId;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
