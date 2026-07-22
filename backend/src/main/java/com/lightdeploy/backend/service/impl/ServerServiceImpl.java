package com.lightdeploy.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.KeyPair;
import com.lightdeploy.backend.entity.Server;
import com.lightdeploy.backend.mapper.ServerMapper;
import com.lightdeploy.backend.service.IServerService;
import com.lightdeploy.backend.util.PathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

@Slf4j
@Service
public class ServerServiceImpl extends ServiceImpl<ServerMapper, Server> implements IServerService {

    @Value("${app.ssh-dir}")
    private String sshDir;

    @Override
    public boolean testSshConnection(Server server) {
        JSch jsch = new JSch();
        Session session = null;
        try {
            int port = server.getPort() != null ? server.getPort() : 22;
            session = jsch.getSession(server.getUsername(), server.getIp(), port);
            
            if (server.getPassword() != null && !server.getPassword().isEmpty()) {
                session.setPassword(server.getPassword());
            }

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            
            // 设置超时时间 5秒
            session.connect(5000);
            return session.isConnected();
        } catch (Exception e) {
            log.error("SSH connection test failed for server: " + server.getIp(), e);
            return false;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    @Override
    public boolean setupSshPasswordless(Server server) {
        if (server.getPassword() == null || server.getPassword().isEmpty()) {
            // Already using key or password not provided, nothing to setup
            return true;
        }

        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            // 1. Ensure local SSH keys exist
            File sshDirFile = new File(PathUtils.resolve(sshDir));
            if (!sshDirFile.exists()) {
                sshDirFile.mkdirs();
            }

            File privKeyFile = new File(sshDirFile, "id_rsa");
            File pubKeyFile = new File(sshDirFile, "id_rsa.pub");

            if (!privKeyFile.exists() || !pubKeyFile.exists()) {
                KeyPair kpair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
                kpair.writePrivateKey(privKeyFile.getAbsolutePath());
                kpair.writePublicKey(pubKeyFile.getAbsolutePath(), "light-deploy@local");
                kpair.dispose();
                log.info("Generated new RSA key pair in {}", sshDirFile.getAbsolutePath());
            }

            // Ensure private key has correct permissions (SSH requires 0600)
            Runtime.getRuntime().exec("chmod 600 " + privKeyFile.getAbsolutePath()).waitFor();

            // 2. Read public key
            String pubKey = new String(Files.readAllBytes(Paths.get(pubKeyFile.getAbsolutePath()))).trim();

            // 3. Connect to remote server using password
            int port = server.getPort() != null ? server.getPort() : 22;
            session = jsch.getSession(server.getUsername(), server.getIp(), port);
            session.setPassword(server.getPassword());
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(10000);

            // 4. Run command to append public key
            String command = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && " +
                    "touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && " +
                    "grep -q -F \"" + pubKey + "\" ~/.ssh/authorized_keys || echo \"" + pubKey + "\" >> ~/.ssh/authorized_keys";

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.connect();

            // Wait for command to finish
            InputStream in = channel.getInputStream();
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    break;
                }
                Thread.sleep(100);
            }

            int exitStatus = channel.getExitStatus();
            if (exitStatus == 0) {
                log.info("Successfully configured SSH passwordless login for server: {}", server.getIp());
                return true;
            } else {
                log.error("Failed to configure SSH passwordless login for server: {}, exit status: {}", server.getIp(), exitStatus);
                return false;
            }
        } catch (Exception e) {
            log.error("Error setting up SSH passwordless login for server: " + server.getIp(), e);
            return false;
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
}