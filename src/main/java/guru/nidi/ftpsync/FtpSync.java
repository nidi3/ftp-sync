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

import java.io.File;
import java.io.IOException;

/**
 *
 */
public class FtpSync {
    static {
        final MavenRepoClasspathEnhancer enhancer = new MavenRepoClasspathEnhancer(FtpSync.class);
//        enhancer.enhanceClassLoader("org.bouncycastle", "bcpkix-jdk15on", "1.48");
        enhancer.enhanceClassLoader();
//        new JarLocalClasspathEnhancer(FtpSync.class).enhanceClassLoader();
    }

    public void sync(Config config) throws IOException {
        try (final Client client = createClient(config)) {
            deleteDirs(client, config.getRemoteDir());
            copyDirs(client, new File(config.getLocalDir()), config.getRemoteDir());
        }
    }

    public Client createClient(Config config) throws IOException {
        if (config.isSecure()) {
            return new SFtpImpl(config);
        }
        return new FtpImpl(config);
    }

    public void copyDirs(Client client, File sourceDir, String destDir) throws IOException {
        System.out.println("Copying to remote: " + sourceDir);
        final String base = destDir.endsWith("/") ? destDir : (destDir + "/");
        final File[] files = sourceDir.listFiles();
        client.createDirectory(destDir);
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    client.copyFile(file, base + file.getName());
                } else if (file.isDirectory()) {
                    copyDirs(client, file, base + file.getName());
                }
            }
        }
    }

    public void deleteDirs(Client client, String path) throws IOException {
        System.out.println("Deleting remote: " + path);
        final String base = path.endsWith("/") ? path : (path + "/");
        for (Client.RemoteFile file : client.listFiles(path)) {
            final String name = file.getName();
            if (file.isFile()) {
                client.deleteFile(base + name);
            } else if (file.isDirectory() && !name.equals(".") && !name.equals("..")) {
                deleteDirs(client, base + name);
            }
        }
        client.deleteDirectory(path);
    }

    public static void main(String[] args) throws IOException {
        new FtpSync().sync(new Config(args));
    }

}
