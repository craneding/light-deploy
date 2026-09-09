package com.lightdeploy.backend.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.lightdeploy.backend.entity.DeployRecord;
import com.lightdeploy.backend.entity.DeployTask;
import com.lightdeploy.backend.entity.DeployProfile;
import com.lightdeploy.backend.entity.Server;
import com.lightdeploy.backend.entity.User;
import com.lightdeploy.backend.exception.GitLabTokenExpiredException;
import com.lightdeploy.backend.util.DeployLogFiles;
import com.lightdeploy.backend.util.PathUtils;
import com.lightdeploy.backend.websocket.DeployLogWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

@Service
public class DeployEngineService {

    /** DB 中只存尾部摘要的行数，全量日志在本地文件 */
    private static final int LOG_TAIL_LINES = 200;

    @Autowired
    private IDeployRecordService deployRecordService;

    @Autowired
    private IServerService serverService;

    @Autowired
    private IDeployProfileService deployProfileService;

    @Autowired
    private IProjectService projectService;
    
    @Autowired
    private com.lightdeploy.backend.mapper.UserMapper userMapper;

    @Autowired
    private DeployLogWebSocketHandler logWebSocketHandler;

    @Autowired
    private GitLabTokenService gitLabTokenService;

    @Value("${gitlab.url}")
    private String gitlabUrl;

    @Value("${app.workspace-dir}")
    private String workspaceDir;

    @Value("${app.artifacts-dir}")
    private String artifactsDir;

    @Value("${app.ssh-dir}")
    private String sshDir;

    @Value("${app.log-dir:./logs}")
    private String logDir;

    /**
     * 部署日志输出器：WS 实时推送 + 本地文件全量落盘 + 内存尾部摘要。
     * 每个部署记录独立一个文件：{app.log-dir}/deploy/{recordId}.log，可手工清理。
     */
    private class DeployLogger implements AutoCloseable {
        private final String taskId;
        private final BufferedWriter writer;
        private final Deque<String> tail = new ArrayDeque<>();

        DeployLogger(String taskId, File logFile) {
            this.taskId = taskId;
            BufferedWriter w = null;
            try {
                w = DeployLogFiles.openWriter(logFile);
            } catch (IOException e) {
                // 文件打开失败不阻断部署，降级为仅 WS 推送
                try {
                    logWebSocketHandler.sendLog(taskId,
                            "[WARNING] Failed to open deploy log file: " + logFile.getAbsolutePath()
                                    + " (" + e.getMessage() + "), fallback to websocket-only.");
                } catch (Exception ignored) {
                }
            }
            this.writer = w;
        }

        synchronized void log(String message) {
            String line = message == null ? "" : message;
            // 脱敏：git 输出可能回显 https://oauth2:TOKEN@host，落盘/推送前统一打码
            line = line.replaceAll("(https?://oauth2:)[^@\\s]+@", "$1***@");
            logWebSocketHandler.sendLog(taskId, line);
            if (writer != null) {
                try {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                } catch (IOException ignored) {
                }
            }
            tail.addLast(line);
            while (tail.size() > LOG_TAIL_LINES) {
                tail.removeFirst();
            }
        }

        synchronized String tailText() {
            return DeployLogFiles.tailText(tail);
        }

        @Override
        public void close() {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Async
    public void executeDeploy(DeployTask task, DeployRecord record, Integer triggerUserId) {
        String taskIdStr = String.valueOf(record.getId());
        File logFile = DeployLogFiles.resolve(logDir, record.getId());

        try (DeployLogger logger = new DeployLogger(taskIdStr, logFile)) {
            try {
                logger.log("=== Starting Deployment for Task ID: " + task.getId() + " ===");

                record.setStatus("RUNNING");
                record.setStartTime(LocalDateTime.now());
                deployRecordService.updateById(record);

                DeployProfile profile = deployProfileService.getById(task.getProfileId());
                if (profile == null) {
                    throw new RuntimeException("Profile not found with ID: " + task.getProfileId());
                }

                Server server = null;
                if (profile.getServerId() != null) {
                    server = serverService.getById(profile.getServerId());
                    if (server == null) {
                        throw new RuntimeException("Server not found with ID: " + profile.getServerId());
                    }
                }

                com.lightdeploy.backend.entity.Project project = projectService.getById(task.getProjectId());
                if (project == null) {
                    throw new RuntimeException("Project not found with ID: " + task.getProjectId());
                }

                String buildScript = (profile.getBuildScript() != null && !profile.getBuildScript().isEmpty())
                        ? profile.getBuildScript() : project.getBuildScript();
                String buildOutputDir = (profile.getBuildOutputDir() != null && !profile.getBuildOutputDir().isEmpty())
                        ? profile.getBuildOutputDir() : project.getBuildOutputDir();
                String preScript = (profile.getPreScript() != null && !profile.getPreScript().isEmpty())
                        ? profile.getPreScript() : project.getPreScript();
                Boolean syncToDeployDir = profile.getSyncToDeployDir() != null ? profile.getSyncToDeployDir() : project.getSyncToDeployDir();
                if (syncToDeployDir == null) {
                    syncToDeployDir = true; // Default to true if not specified
                }
                String deployDir = (profile.getDeployDir() != null && !profile.getDeployDir().isEmpty())
                        ? profile.getDeployDir() : project.getDeployDir();
                String postScript = (profile.getPostScript() != null && !profile.getPostScript().isEmpty())
                        ? profile.getPostScript() : project.getPostScript();

                // 0. Fetch Gitlab Project Info and Clone/Checkout code
                logger.log(">>> 0. Preparing Source Code from GitLab");
                String workspaceDir = prepareSourceCode(project.getGitlabProjectId(), record, logger, triggerUserId);

                // 1. Execute Local Build Script
                if (buildScript != null && !buildScript.isEmpty()) {
                    logger.log(">>> 1. Executing Local Build Script in " + workspaceDir);
                    executeLocalCommand(buildScript, logger, workspaceDir);

                    // Save artifacts if buildOutputDir is configured
                    if (buildOutputDir != null && !buildOutputDir.trim().isEmpty()) {
                        logger.log(">>> 1.1 Saving Build Artifacts from " + buildOutputDir);
                        String artifactRoot = PathUtils.resolve(artifactsDir) + "/" + record.getId();
                        File artifactDir = new File(artifactRoot);
                        if (!artifactDir.exists()) {
                            artifactDir.mkdirs();
                        }
                        // Copy build output to artifact directory
                        String sourcePath = workspaceDir + "/" + buildOutputDir;

                        // Check if the source path is a directory or a file
                        File sourceFile = new File(sourcePath);
                        if (sourceFile.exists()) {
                            if (sourceFile.isDirectory()) {
                                // If it's a directory, copy its contents
                                executeLocalCommand("cp -R " + sourcePath + "/* " + artifactRoot + "/", logger, workspaceDir);
                            } else {
                                // If it's a file, copy the file itself
                                executeLocalCommand("cp " + sourcePath + " " + artifactRoot + "/", logger, workspaceDir);
                            }
                            logger.log("Artifacts saved to " + artifactRoot);
                        } else {
                            logger.log("[WARNING] Artifact source path does not exist: " + sourcePath);
                        }
                    }
                }

                // 2. Execute Pre-script on Remote Server
                if (preScript != null && !preScript.isEmpty()) {
                    if (server == null) throw new RuntimeException("Pre-script requires a server but none is associated with this profile.");
                    logger.log(">>> 2. Executing Pre-Script on Remote Server");
                    executeRemoteCommand(server, preScript, logger);
                }

                // 3. Sync files to remote via rsync
                if (syncToDeployDir && deployDir != null && !deployDir.isEmpty()) {
                    if (server == null) throw new RuntimeException("Syncing to deploy directory requires a server but none is associated with this profile.");
                    logger.log(">>> 3. Syncing files via rsync to " + server.getIp() + ":" + deployDir);

                    String sourcePath = workspaceDir;
                    if (buildOutputDir != null && !buildOutputDir.trim().isEmpty()) {
                        sourcePath = workspaceDir + "/" + buildOutputDir;
                    }

                    // Ensure source path ends with / for rsync to copy contents rather than the directory itself
                    File sourceFileForRsync = new File(sourcePath);
                    if (sourceFileForRsync.isDirectory() && !sourcePath.endsWith("/")) {
                        sourcePath += "/";
                    }

                    // Create rsync command. We now rely on passwordless SSH.
                    int port = server.getPort() != null ? server.getPort() : 22;
                    String keyPath = PathUtils.resolve(sshDir) + "/id_rsa";
                    // Ensure private key has correct permissions (SSH requires 0600)
                    executeLocalCommand("chmod 600 " + keyPath, logger, workspaceDir);
                    String sshCmd = "ssh -p " + port + " -o StrictHostKeyChecking=no -i " + keyPath;
                    String rsyncCmd = String.format("rsync -avz --delete -e \"%s\" %s %s@%s:%s",
                            sshCmd, sourcePath, server.getUsername(), server.getIp(), deployDir);

                    executeLocalCommand(rsyncCmd, logger, workspaceDir);
                }

                // 4. Execute Post-script on Remote Server
                if (postScript != null && !postScript.isEmpty()) {
                    if (server == null) throw new RuntimeException("Post-script requires a server but none is associated with this profile.");
                    logger.log(">>> 4. Executing Post-Script on Remote Server");
                    executeRemoteCommand(server, postScript, logger);
                }

                logger.log("=== Deployment Completed Successfully ===");
                record.setStatus("SUCCESS");
            } catch (Exception e) {
                // GitLab 授权失效给明确中文提示，不再裸露 401 JSON
                String reason = (e instanceof GitLabTokenExpiredException)
                        ? "GitLab 授权已过期，请重新登录后再发起部署。"
                        : String.valueOf(e.getMessage());
                String errorMsg = "=== Deployment Failed: " + reason + " ===";
                try {
                    logger.log(errorMsg);
                } catch (Exception ignored) {
                }
                record.setStatus("FAILED");
            } finally {
                record.setEndTime(LocalDateTime.now());
                // DB 只存尾部摘要（最后 200 行），全量在本地文件
                // {app.log-dir}/deploy/{recordId}.log，可手工清理
                try {
                    String tail = logger.tailText();
                    if (tail == null || tail.isEmpty()) {
                        tail = "任务已结束，日志文件未生成（" + logFile.getAbsolutePath() + "）";
                    }
                    record.setLogs(tail);
                } catch (Exception ignored) {
                    if (record.getLogs() == null) {
                        record.setLogs("任务已结束，但读取日志摘要失败。");
                    }
                }
                deployRecordService.updateById(record);
            }
        }
    }

    private void executeLocalCommand(String command, DeployLogger logger, String workingDir) throws Exception {
        // Create a temporary script file to execute
        File tempScript = File.createTempFile("deploy_script_", ".sh");
        tempScript.setExecutable(true);
        try {
            java.nio.file.Files.write(tempScript.toPath(), command.getBytes());

            ProcessBuilder pb = new ProcessBuilder("/bin/bash", tempScript.getAbsolutePath());
            if (workingDir != null) {
                File dir = new File(workingDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                pb.directory(dir);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.log(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.log("Local command exited with code " + exitCode);
                throw new RuntimeException("Local command exited with code " + exitCode);
            }
        } finally {
            tempScript.delete();
        }
    }

    private void executeRemoteCommand(Server server, String command, DeployLogger logger) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(server.getUsername(), server.getIp(), server.getPort() != null ? server.getPort() : 22);
        session.setPassword(server.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");

        ChannelExec channel = null;
        try {
            session.connect(10000); // 10 seconds timeout

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setErrStream(null);

            InputStream in = channel.getInputStream();
            InputStream err = channel.getExtInputStream();
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(err));

            String line;
            while (!channel.isClosed()) {
                while ((line = reader.readLine()) != null) {
                    logger.log(line);
                }
                while ((line = errReader.readLine()) != null) {
                    logger.log("[ERROR] " + line);
                }
                Thread.sleep(100);
            }

            // Final read
            while ((line = reader.readLine()) != null) {
                logger.log(line);
            }
            while ((line = errReader.readLine()) != null) {
                logger.log("[ERROR] " + line);
            }

            if (channel.getExitStatus() != 0) {
                logger.log("Remote command exited with status " + channel.getExitStatus());
                throw new RuntimeException("Remote command exited with status " + channel.getExitStatus());
            }

        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * 部署用 token 归属解析：优先触发用户（经自动刷新），其授权彻底失效时
     * 兜底库内任意可用用户，保证单用户/旧数据场景不断；都不行则抛明确异常。
     * 返回实际提供 token 的用户 ID，后续 401 重试刷新该用户。
     */
    private Integer resolveDeployTokenUserId(Integer triggerUserId, DeployLogger logger) {
        if (triggerUserId != null) {
            try {
                gitLabTokenService.getValidAccessToken(triggerUserId);
                return triggerUserId;
            } catch (GitLabTokenExpiredException e) {
                logger.log("[WARNING] 触发用户的 GitLab 授权已失效，尝试使用其他可用用户授权。");
            }
        }
        for (User candidate : userMapper.selectList(null)) {
            if (candidate.getAccessToken() == null || candidate.getAccessToken().isEmpty()) {
                continue;
            }
            try {
                gitLabTokenService.getValidAccessToken(candidate.getId());
                return candidate.getId();
            } catch (GitLabTokenExpiredException ignored) {
            }
        }
        throw new GitLabTokenExpiredException("GitLab 授权已过期，请重新登录后再发起部署。");
    }

    /** 取 GitLab 项目信息：401 invalid_token 时强制刷新供 token 用户后只重试一次，仍失败转 440 */
    private Map<String, Object> fetchGitLabProject(RestTemplate restTemplate, String gitlabApiUrl,
                                                   Integer tokenUserId) {
        try {
            return doFetchGitLabProject(restTemplate, gitlabApiUrl,
                    gitLabTokenService.getValidAccessToken(tokenUserId));
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            String body = e.getResponseBodyAsString();
            if (body == null || !body.contains("invalid_token")) {
                throw e;
            }
            String newToken = gitLabTokenService.forceRefreshAccessToken(tokenUserId);
            try {
                return doFetchGitLabProject(restTemplate, gitlabApiUrl, newToken);
            } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized retry) {
                throw new GitLabTokenExpiredException("GitLab 授权已过期，请重新登录后再发起部署。", retry);
            }
        }
    }

    private Map<String, Object> doFetchGitLabProject(RestTemplate restTemplate, String gitlabApiUrl, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                gitlabApiUrl, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
        return response.getBody();
    }

    private String prepareSourceCode(Integer gitlabProjectId, DeployRecord record, DeployLogger logger,
                                       Integer triggerUserId) throws Exception {
        // 优先使用触发用户的 token（自动刷新），其不可用时兜底库内任意可用用户，保证单用户部署不断
        Integer tokenUserId = resolveDeployTokenUserId(triggerUserId, logger);

        String gitlabApiUrl = gitlabUrl + "/api/v4/projects/" + gitlabProjectId;

        RestTemplate restTemplate = new RestTemplate();

        Map<String, Object> projectData = fetchGitLabProject(restTemplate, gitlabApiUrl, tokenUserId);
        if (projectData == null || !projectData.containsKey("http_url_to_repo")) {
            throw new RuntimeException("Could not retrieve repository URL from GitLab.");
        }

        String repoUrl = (String) projectData.get("http_url_to_repo");
        // Inject token into URL for basic auth clone (OAuth2 token can be used as username oauth2 with token as password, or simply as password with empty user)
        // Format: https://oauth2:TOKEN@gitlab.example.com/group/project.git
        // 重新读取最新 token（401 重试中可能已刷新）
        String token = gitLabTokenService.getValidAccessToken(tokenUserId);
        String authRepoUrl = repoUrl;
        if (authRepoUrl.startsWith("https://")) {
            authRepoUrl = authRepoUrl.replace("https://", "https://oauth2:" + token + "@");
        } else if (authRepoUrl.startsWith("http://")) {
            authRepoUrl = authRepoUrl.replace("http://", "http://oauth2:" + token + "@");
        }

        // 2. Determine workspace directory
        String workspaceRoot = PathUtils.resolve(workspaceDir);
        File rootDir = new File(workspaceRoot);
        if (!rootDir.exists()) {
            boolean created = rootDir.mkdirs();
            if (!created) {
                logger.log("Warning: Failed to create workspace root directory: " + workspaceRoot);
            }
        }

        String projectName = (String) projectData.get("path");
        String workDir = workspaceRoot + "/" + projectName + "_" + gitlabProjectId;
        File projectDir = new File(workDir);
        File gitDir = new File(workDir, ".git");

        // 3. Clone or Fetch
        if (!projectDir.exists() || !gitDir.exists()) {
            if (projectDir.exists()) {
                logger.log("Cleaning up invalid workspace directory...");
                executeLocalCommand("rm -rf " + workDir, logger, workspaceRoot);
            }
            logger.log("Cloning repository...");
            executeLocalCommand("git clone " + authRepoUrl + " " + workDir, logger, workspaceRoot);
        } else {
            logger.log("Updating remote URL and fetching latest changes...");
            // Update remote URL with the latest token in case it expired or wasn't set correctly before
            executeLocalCommand("git remote set-url origin " + authRepoUrl, logger, workDir);
            executeLocalCommand("git fetch --all", logger, workDir);
        }

        // 4. Checkout the specific ref
        String gitRef = record.getCommitId() != null ? record.getCommitId() : record.getBranch();
        if (gitRef == null || gitRef.isEmpty()) {
            throw new RuntimeException("Git reference (branch/tag/commit) is not specified.");
        }
        logger.log("Checking out " + gitRef + "...");
        // Clean up any local changes before checkout
        executeLocalCommand("git reset --hard", logger, workDir);
        executeLocalCommand("git clean -fd", logger, workDir);
        executeLocalCommand("git checkout " + gitRef, logger, workDir);

        // If it's a branch, pull the latest
        if (record.getBranch() != null && !record.getBranch().isEmpty() && record.getCommitId() == null) {
             try {
                 executeLocalCommand("git pull origin " + gitRef, logger, workDir);
             } catch (Exception e) {
                 logger.log("Warning: git pull failed, it might be a tag or detached HEAD. " + e.getMessage());
             }
        }

        return workDir;
    }
}
