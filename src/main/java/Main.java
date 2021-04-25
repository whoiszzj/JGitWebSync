import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(Main.class);
        String remoteURL = "git@gitee.com:kingqi_admin/pic-go1.git";
        String localPath = "E:\\temp\\JGit";
        File file = Paths.get(localPath, "test.txt").toFile();
        // ========================================================================
        // 从远程clone库
        JGitWebSync remoteUtil = new JGitWebSync(remoteURL, localPath);
        if (remoteUtil.getGit() == null) {
            return;
        }
        try {
            if (!file.exists()) {
                if(!file.createNewFile()) {
                    logger.error("create file error");
                }
            }
            // 写出数据
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write("hello world " + System.currentTimeMillis());
            writer.close();
            // 测试添加文件
            remoteUtil.add("test.txt");
            remoteUtil.push("add test.txt");
        } catch (IOException e) {
            logger.error("create or write file error: " + e.getMessage());
        }
        remoteUtil.close();
        // ========================================================================
        // 从本地加载库
        JGitWebSync localUtil = new JGitWebSync(localPath);
        if (localUtil.getGit() == null) {
            return;
        }
        // 测试删除文件
        localUtil.remove("test.txt");
        localUtil.push("delete test.txt");
        localUtil.close();
    }
}
