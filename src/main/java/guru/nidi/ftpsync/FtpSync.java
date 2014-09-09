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

import guru.nidi.ftpsync.cpmagic.MavenRepoClasspathEnhancer;
import guru.nidi.ftpsync.fs.*;

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

    private static final AbstractFileFilter SELECT_FILES = new AbstractFileFilter() {
        @Override
        public boolean accept(AbstractFile abstractFile) {
            return abstractFile.isFile();
        }
    };
    private static final AbstractFileFilter SELECT_DIRS = new AbstractFileFilter() {
        @Override
        public boolean accept(AbstractFile abstractFile) {
            return abstractFile.isDirectory() && !abstractFile.getName().equals(".") && !abstractFile.getName().equals("..");
        }
    };

    public void sync(Config config) throws IOException {
        try (final FileSystem fileSystem = createClient(config)) {
            deleteDirs(fileSystem, config.getRemoteDir());
            copyDirs(fileSystem, new File(config.getLocalDir()), config.getRemoteDir());
        }
    }

    public FileSystem createClient(Config config) throws IOException {
        if (config.isSecure()) {
            return new SftpFileSystem(config);
        }
        return new FtpFileSystem(config);
    }

    public void copyDirs(final FileSystem fileSystem, File sourceDir, String destDir) throws IOException {
        final String source = sourceDir.getAbsolutePath();
        System.out.println("Copying to remote: " + source);
        fileSystem.createDirectory(destDir);
        final String base = destDir.endsWith("/") ? destDir : (destDir + "/");

        Utils.doProgressively(new LocalFileSystem().listFiles(source, SELECT_FILES), new Utils.ProgressWorker<AbstractFile>() {
            @Override
            public String itemName(AbstractFile item) {
                return item.getName();
            }

            @Override
            public void processItem(AbstractFile item) throws IOException {
                fileSystem.copyFile(item.asFile(), base + item.getName());
            }
        });

        for (AbstractFile dir : new LocalFileSystem().listFiles(source, SELECT_DIRS)) {
            copyDirs(fileSystem, dir.asFile(), base + dir.getName());
        }
    }

    public void deleteDirs(final FileSystem fileSystem, String path) throws IOException {
        System.out.println("Deleting remote: " + path);
        final String base = path.endsWith("/") ? path : (path + "/");
        Utils.doProgressively(fileSystem.listFiles(path, SELECT_FILES), new Utils.ProgressWorker<AbstractFile>() {
            @Override
            public String itemName(AbstractFile item) {
                return item.getName();
            }

            @Override
            public void processItem(AbstractFile item) throws Exception {
                fileSystem.deleteFile(base + item.getName());
            }
        });
        for (AbstractFile dir : fileSystem.listFiles(path, SELECT_DIRS)) {
            deleteDirs(fileSystem, base + dir.getName());
        }
        fileSystem.deleteDirectory(path);
    }

    public static void main(String[] args) throws IOException {
        new FtpSync().sync(new Config(args));
    }

}
