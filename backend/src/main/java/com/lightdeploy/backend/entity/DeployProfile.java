package com.lightdeploy.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("deploy_profiles")
public class DeployProfile {
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    private Integer projectId;
    
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer serverId;
    
    private String name;
    
    private String buildScript;

    private String buildOutputDir;

    private String deployDir;
    private Boolean syncToDeployDir;

    private String preScript;
    private String postScript;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}