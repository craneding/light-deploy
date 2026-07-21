package com.lightdeploy.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightdeploy.backend.entity.Server;

public interface IServerService extends IService<Server> {
    
    /**
     * 测试服务器 SSH 连接
     * @param server 服务器信息
     * @return 连接成功返回 true，失败抛出异常或返回 false
     */
    boolean testSshConnection(Server server);

    /**
     * 配置 SSH 免密登录
     * @param server 服务器信息
     * @return 配置成功返回 true，失败抛出异常或返回 false
     */
    boolean setupSshPasswordless(Server server);
}