package com.lightdeploy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("servers")
public class Server {
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    private String name;
    private String ip;
    private Integer port;
    private String username;
    private String password;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}