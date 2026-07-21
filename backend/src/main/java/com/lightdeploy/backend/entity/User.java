package com.lightdeploy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    private Integer gitlabId;
    private String username;
    private String email;
    private String avatarUrl;
    private String accessToken;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
