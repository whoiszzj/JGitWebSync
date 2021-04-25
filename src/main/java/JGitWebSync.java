import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * 本类利用JGit实现了一些简单的文件传输操作，可以用于一些简单的基于服务器的程序开发
 * 适用的类似前后端框架：
 * <client1> - <serve> - <JGitWebSync> - <Cloud> - <JGitWebSync>- <serve> - <client2>
 * |- front -| |------------------------ back ----------------------------| |- front -|
 *
 * @author kingqi
 * @date 2021/04/23
 */
public class JGitWebSync {
    /**
     * 访问权限
     */

    private final SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
        @Override
        protected void configure(OpenSshConfig.Host host, Session session) {

        }

        @Override
        protected JSch createDefaultJSch(FS fs) throws JSchException {
            JSch defaultJSch = super.createDefaultJSch(fs);

            //首先移除全部的身份认证
            defaultJSch.removeAllIdentity();
            //直接加入私钥文件路径
            defaultJSch.addIdentity("~/.ssh/id_rsa");
            //或者读取私钥后加入

            return defaultJSch;
        }
    };

    private final TransportConfigCallback transportConfigCallback = transport -> {
        if (transport instanceof SshTransport) {
            SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(JGitWebSync.this.sshSessionFactory);
        }
    };

    /**
     * 定义一个logger对象，用于log的输出
     */
    private final Logger logger = LoggerFactory.getLogger(JGitWebSync.class);

    /**
     * 定义Git对象
     */
    private Git git = null;

    /**
     * 构造函数，将远端的仓库克隆到本地
     *
     * @param remoteURL 远端地址
     * @param localPath 本地路径
     */
    public JGitWebSync(String remoteURL, String localPath) {
        // =============================================================================================================
        // 先将本地的localPath文件夹清空并建立
        File repoDir = readyLocalDir(localPath);
        // 如果为空则返回
        if (repoDir == null) {
            this.logger.error("init Git error");
            return;
        }
        // 从remoteURL处clone
        try {
            this.git = Git.cloneRepository()
                    .setTransportConfigCallback(this.transportConfigCallback)
                    .setURI(remoteURL)
                    .setDirectory(repoDir)
                    .call();
            this.logger.info("Cloning from " + remoteURL + " to " + this.git.getRepository());
        } catch (Exception e) {
            this.logger.error("clone error: " + e.getMessage());
        }
    }

    /**
     * 构造函数，加载本地路径下的仓库，如果该仓库存在则加载后进行pull同步，不存在则放弃加载
     *
     * @param localPath 本地路径
     */
    public JGitWebSync(String localPath) {
        // =============================================================================================================
        // 从本地加载仓库
        try {
            Repository repository = new FileRepositoryBuilder().setGitDir(Paths.get(localPath, ".git").toFile()).build();
            this.git = Git.wrap(repository);
            this.pull();
        } catch (IOException e) {
            logger.error("get exist repository error: " + e.getMessage());
            this.git = null;
        }
    }

    /**
     * 用作对文件的增加或修改操作
     *
     * @param fileName 增加或修改的文件名称
     */
    public void add(String fileName) {
        try {
            this.logger.info("add file: " + fileName);
            this.git.add().addFilepattern(fileName).call();
        } catch (GitAPIException e) {
            logger.error("add file error" + e.getMessage());
        }
    }

    /**
     * 用作对文件的删除
     *
     * @param fileName 文件名称
     */
    public void remove(String fileName) {
        try {
            this.logger.info("remove file: " + fileName);
            this.git.rm().addFilepattern(fileName).call();
        } catch (GitAPIException e) {
            this.logger.error("remove file error: " + e.getMessage());
        }
    }

    /**
     * 提交更改并实现上传
     *
     * @param message 备注
     */
    public void push(String message) {
        try {
            this.logger.info("commit and push: " + message);
            // 提交
            this.git.commit().setMessage(message).call();
            // 推送
            this.git.push()
                    .setTransportConfigCallback(this.transportConfigCallback)
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec("master"))
                    .call();
        } catch (GitAPIException e) {
            this.logger.error("push error " + e.getMessage());
        }
    }

    /**
     * 拉取消息
     */
    public void pull() {
        try {
            // 拉取
            this.git.pull()
                    .setTransportConfigCallback(this.transportConfigCallback)
                    .setRemote("origin")
                    .call();
        } catch (GitAPIException e) {
            this.logger.error("pull error " + e.getMessage());
        }
    }

    /**
     * 释放Git
     */
    public void close() {
        this.git.close();
    }

    /**
     * 获取Git对象
     *
     * @return {@link Git}
     */
    public Git getGit() {
        return git;
    }

    /**
     * 为仓库的创建或者克隆准备好本地的目录
     *
     * @param localDirPath 当地的目录路径
     * @return {@link File} 成功则返回一个存在的目录文件且该目录为空，失败返回null
     */
    private File readyLocalDir(String localDirPath) {
        // 获取目录
        File repoDir = new File(localDirPath);
        if (!repoDir.exists()) {
            // 如果不存在则创建一个目录
            if (repoDir.mkdir()) {
                this.logger.error("create dir " + localDirPath + " error");
                return null;
            }
        } else {
            // 如果存在 则判断是否为一个目录
            if (repoDir.isDirectory()) {
                if (repoDir.list() != null) {
                    // 如果非空则删除并重建
                    this.logger.info("dir " + localDirPath + " is not empty, delete it then make it");
                    try {
                        // 删除文件夹
                        FileUtils.deleteDirectory(repoDir);
                        // 创建文件夹
                        if (!repoDir.mkdirs()) {
                            this.logger.error("create dir " + localDirPath + " error");
                            return null;
                        }
                    } catch (IOException e) {
                        this.logger.error("delete dir " + localDirPath + " error");
                        return null;
                    }
                }
            }
        }
        return repoDir;
    }
}
