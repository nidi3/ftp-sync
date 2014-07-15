package guru.nidi.ftpsync;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class SFtpImpl implements Client {
    private final SSHClient ssh;
    private final SFTPClient client;

    public SFtpImpl(Config config) throws IOException {
        ssh = new SSHClient();
        ssh.loadKnownHosts();
        ssh.connect(config.getHost());
        if (config.getPassword() != null) {
            ssh.authPassword(config.getUsername(), config.getPassword());
        } else {
            ssh.authPublickey(config.getUsername(), config.getIdentity());
        }
        client = ssh.newSFTPClient();
        client.getFileTransfer().setPreserveAttributes(false);
    }

    public void close() throws IOException {
        client.close();
        ssh.disconnect();
    }

    public void deleteFile(String name) throws IOException {
        client.rm(name);
    }

    public void deleteDirectory(String name) throws IOException {
        client.rmdir(name);
    }

    public void copyFile(File local, String dest) throws IOException {
        client.put(local.getAbsolutePath(), dest);
    }

    public void createDirectory(String name) throws IOException {
        client.mkdir(name);
    }

    @Override
    public List<RemoteFile> listFiles(String dir) throws IOException {
        List<RemoteFile> res = new ArrayList<>();
        for (RemoteResourceInfo info : client.ls(dir)) {
            res.add(new RemoteFileImpl(info));
        }
        return res;
    }

    private class RemoteFileImpl implements RemoteFile {
        private final RemoteResourceInfo info;

        public RemoteFileImpl(RemoteResourceInfo info) {
            this.info = info;
        }

        @Override
        public boolean isFile() {
            return info.isRegularFile();
        }

        @Override
        public boolean isDirectory() {
            return info.isDirectory();
        }

        @Override
        public String getName() {
            return info.getName();
        }
    }
}
