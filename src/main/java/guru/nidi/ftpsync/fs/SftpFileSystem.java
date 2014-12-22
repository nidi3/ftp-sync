/*
 * Copyright (C) 2014 Stefan Niederhauser (nidin@gmx.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guru.nidi.ftpsync.fs;

import guru.nidi.ftpsync.Config;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceFilter;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class SftpFileSystem extends FileSystemBase {
    private final SSHClient ssh;
    private final SFTPClient client;

    public SftpFileSystem(String basedir, Config config) throws IOException {
        super(basedir);
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
        client.rm(expand(name));
    }

    public void deleteDirectory(String name) throws IOException {
        client.rmdir(expand(name));
    }

    public void putFile(File local, String dest) throws IOException {
        client.put(local.getAbsolutePath(), expand(dest));
    }

    public void getFile(File local, String dest) throws IOException {
        client.get(local.getAbsolutePath(), expand(dest));
    }

    public void createDirectory(String name) throws IOException {
        client.mkdirs(expand(name));
    }

    @Override
    public List<AbstractFile> listFiles(String dir, AbstractFileFilter filter) throws IOException {
        List<AbstractFile> res = new ArrayList<>();
        for (RemoteResourceInfo info : client.ls(expand(dir), new RemoteResourceFilterImpl(filter))) {
            res.add(new AbstractFileImpl(info));
        }
        return res;
    }

    private static class RemoteResourceFilterImpl implements RemoteResourceFilter {
        private final AbstractFileFilter filter;

        private RemoteResourceFilterImpl(AbstractFileFilter filter) {
            this.filter = filter;
        }

        @Override
        public boolean accept(RemoteResourceInfo resource) {
            return filter.accept(new AbstractFileImpl(resource));
        }
    }

    private static class AbstractFileImpl implements AbstractFile {
        private final RemoteResourceInfo info;

        public AbstractFileImpl(RemoteResourceInfo info) {
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

        @Override
        public File asFile() {
            throw new UnsupportedOperationException();
        }
    }
}
