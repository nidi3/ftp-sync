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
package guru.nidi.ftpsync;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class FtpImpl implements Client {
    private final FTPClient client;

    public FtpImpl(Config config) throws IOException {
        client = new FTPClient();
        client.connect(config.getHost());
        int reply = client.getReplyCode();

        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect();
            throw new IOException("FTP server refused connection.");
        }
        final boolean login = client.login(config.getUsername(), config.getPassword());
        if (!login) {
            throw new FtpException("Could not login", client.getReplyStrings());
        }
        client.setFileType(FTP.BINARY_FILE_TYPE);
    }

    public void deleteFile(String name) throws IOException {
        if (!client.deleteFile(name)) {
            throw new FtpException("Could not delete file " + name, client.getReplyStrings());
        }
    }

    public void deleteDirectory(String name) throws IOException {
        if (!client.removeDirectory(name)) {
            throw new FtpException("Could not delete directory " + name, client.getReplyStrings());
        }
    }

    public void copyFile(File local, String dest) throws IOException {
        try (InputStream in = new FileInputStream(local)) {
            if (!client.storeFile(dest, in)) {
                throw new FtpException("Could not copy file " + local + " to " + dest, client.getReplyStrings());
            }
        }
    }

    public void createDirectory(String name) throws IOException {
        if (!client.makeDirectory(name)) {
            throw new FtpException("Could not create directory " + name, client.getReplyStrings());
        }
    }

    @Override
    public List<RemoteFile> listFiles(String dir) throws IOException {
        List<RemoteFile> res = new ArrayList<>();
        for (FTPFile file : client.listFiles(dir)) {
            res.add(new RemoteFileImpl(file));
        }
        return res;
    }

    @Override
    public void close() {
    }

    private class RemoteFileImpl implements RemoteFile {
        private final FTPFile file;

        private RemoteFileImpl(FTPFile file) {
            this.file = file;
        }

        @Override
        public boolean isFile() {
            return file.isFile();
        }

        @Override
        public boolean isDirectory() {
            return file.isDirectory();
        }

        @Override
        public String getName() {
            return file.getName();
        }
    }
}
