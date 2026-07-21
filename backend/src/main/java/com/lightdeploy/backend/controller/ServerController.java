package com.lightdeploy.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightdeploy.backend.entity.Server;
import com.lightdeploy.backend.service.IServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/servers")
public class ServerController {

    @Autowired
    private IServerService serverService;

    @GetMapping
    public ResponseEntity<?> getAllServers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        
        QueryWrapper<Server> query = new QueryWrapper<>();
        query.orderByDesc("created_at");
        
        if (StringUtils.hasText(search)) {
            query.and(q -> q.like("name", search.trim()).or().like("ip", search.trim()));
        }

        if (page != null && size != null) {
            Page<Server> serverPage = new Page<>(page, size);
            serverService.page(serverPage, query);
            
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("list", serverPage.getRecords());
            result.put("total", serverPage.getTotal());
            result.put("current", serverPage.getCurrent());
            result.put("size", serverPage.getSize());
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.ok(serverService.list(query));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Server> getServerById(@PathVariable Integer id) {
        Server server = serverService.getById(id);
        if (server == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(server);
    }

    @PostMapping
    public ResponseEntity<?> createServer(@RequestBody Server server) {
        if (!serverService.testSshConnection(server)) {
            return ResponseEntity.badRequest().body("SSH 连接验证失败，请检查服务器IP、端口、账号或密码");
        }
        
        // 配置免密登录
        serverService.setupSshPasswordless(server);

        server.setCreatedAt(LocalDateTime.now());
        server.setUpdatedAt(LocalDateTime.now());
        serverService.save(server);
        return ResponseEntity.ok(server);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateServer(@PathVariable Integer id, @RequestBody Server server) {
        Server serverToTest = new Server();
        serverToTest.setIp(server.getIp());
        serverToTest.setPort(server.getPort());
        serverToTest.setUsername(server.getUsername());
        serverToTest.setPassword(server.getPassword());
        
        Server dbServer = serverService.getById(id);
        if (dbServer != null) {
            if (serverToTest.getPassword() == null || serverToTest.getPassword().isEmpty()) {
                serverToTest.setPassword(dbServer.getPassword());
            }
            if (serverToTest.getIp() == null || serverToTest.getIp().isEmpty()) {
                serverToTest.setIp(dbServer.getIp());
            }
            if (serverToTest.getUsername() == null || serverToTest.getUsername().isEmpty()) {
                serverToTest.setUsername(dbServer.getUsername());
            }
            if (serverToTest.getPort() == null) {
                serverToTest.setPort(dbServer.getPort());
            }
        }
        
        if (!serverService.testSshConnection(serverToTest)) {
            return ResponseEntity.badRequest().body("SSH 连接验证失败，请检查服务器IP、端口、账号或密码");
        }

        // 配置免密登录
        serverService.setupSshPasswordless(serverToTest);

        server.setId(id);
        server.setUpdatedAt(LocalDateTime.now());
        serverService.updateById(server);
        return ResponseEntity.ok(server);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServer(@PathVariable Integer id) {
        serverService.removeById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test-ssh")
    public ResponseEntity<String> testSshConnection(@RequestBody Server server) {
        // If server ID is provided, fetch latest info from DB to avoid sending password from frontend
        if (server.getId() != null) {
            Server dbServer = serverService.getById(server.getId());
            if (dbServer != null) {
                // merge properties
                if (server.getPassword() == null) {
                    server.setPassword(dbServer.getPassword());
                }
                if (server.getIp() == null) {
                    server.setIp(dbServer.getIp());
                }
                if (server.getUsername() == null) {
                    server.setUsername(dbServer.getUsername());
                }
                if (server.getPort() == null) {
                    server.setPort(dbServer.getPort());
                }
            }
        }
        
        boolean success = serverService.testSshConnection(server);
        if (success) {
            return ResponseEntity.ok("SSH connection successful");
        } else {
            return ResponseEntity.status(400).body("SSH connection failed");
        }
    }
}