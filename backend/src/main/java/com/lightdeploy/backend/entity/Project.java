package com.lightdeploy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("projects")
public class Project {
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    private Integer gitlabProjectId;
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