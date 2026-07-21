package com.lightdeploy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("deploy_records")
public class DeployRecord {
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    private Integer taskId;
    private Integer triggerUserId;
    private String branch;
    private String commitId;
    
    private String status; // PENDING, RUNNING, SUCCESS, FAILED
    private String logs; // Or logFilePath
    
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
