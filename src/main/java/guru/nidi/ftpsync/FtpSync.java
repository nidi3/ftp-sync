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
import guru.nidi.ftpsync.fs.FileSystem;

import java.io.*;
import java.util.*;
import java.util.zip.Adler32;

/**
 *
 */
public class FtpSync implements Closeable {
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

    private final String localDir;
    private final String remoteDir;
    private final FileSystem fileSystem;

    public FtpSync(Config config) throws IOException {
        localDir = config.getLocalDir();
        remoteDir = config.getRemoteDir();
        fileSystem = config.isSecure() ? new SftpFileSystem(config) : new FtpFileSystem(config);
    }

    @Override
    public void close() throws IOException {
        fileSystem.close();
    }

    public static void main(String[] args) throws IOException {
        try (final FtpSync sync = new FtpSync(new Config(args))) {
            sync.sync();
        }
    }

    public void sync() throws IOException {
        final Analysis analysis = new Analysis(new InputStreamReader(new FileInputStream(syncFile()), "utf-8"));
        analyze("/", analysis);
        delete(analysis);
        copy("/", analysis);
        analysis.saveState(new OutputStreamWriter(new FileOutputStream(syncFile()), "utf-8"));
    }

    private File syncFile() throws IOException {
        final File local = new File(localDir);
        final File sync = new File(local.getParentFile(), local.getName() + ".sync");
        if (!sync.exists()) {
            sync.createNewFile();
        }
        return sync;
    }

    public boolean analyze(final String dir, final Analysis analysis) throws IOException {
        System.out.println("Analyzing remote: " + dir);
        final boolean[] keepAny = new boolean[]{false};
        Utils.doProgressively(fileSystem.listFiles(remoteDir + dir, SELECT_FILES), new Utils.ProgressWorker<AbstractFile>() {
            @Override
            public String itemName(AbstractFile item) {
                return item.getName();
            }

            @Override
            public void processItem(AbstractFile item) throws Exception {
                keepAny[0] |= analysis.willKeepFile(localDir, withSlash(dir) + item.getName());
            }
        });
        for (AbstractFile sub : fileSystem.listFiles(remoteDir + dir, SELECT_DIRS)) {
            keepAny[0] |= analyze(withSlash(dir) + sub.getName(), analysis);
        }
        if (!keepAny[0]) {
            analysis.addDirToDelete(dir);
        }
        return keepAny[0];
    }

    public void delete(final Analysis analysis) throws IOException {
        System.out.println("Deleting remote...");
        Utils.doProgressively(analysis.getDeletes(), new Utils.ProgressWorker<String>() {
            @Override
            public String itemName(String item) {
                return item;
            }

            @Override
            public void processItem(String item) throws Exception {
                if (item.endsWith("/")) {
                    fileSystem.deleteDirectory(remoteDir + item);
                } else {
                    fileSystem.deleteFile(remoteDir + item);
                }
            }
        });
    }

    public void copy(final String dir, final Analysis analysis) throws IOException {
        System.out.println("Copying to remote: " + dir);

        if (analysis.shouldCopy(localDir, dir)) {
            fileSystem.createDirectory(remoteDir + dir);
        }

        Utils.doProgressively(new LocalFileSystem().listFiles(localDir + dir, SELECT_FILES), new Utils.ProgressWorker<AbstractFile>() {
            @Override
            public String itemName(AbstractFile item) {
                return item.getName();
            }

            @Override
            public void processItem(AbstractFile item) throws IOException {
                final String fullname = withSlash(dir) + item.getName();
                if (analysis.shouldCopy(localDir, fullname)) {
                    fileSystem.copyFile(item.asFile(), remoteDir + fullname);
                }
            }
        });

        for (AbstractFile sub : new LocalFileSystem().listFiles(localDir + dir, SELECT_DIRS)) {
            copy(withSlash(dir) + sub.getName(), analysis);
        }
    }

    private static String withSlash(String s) {
        return s.endsWith("/") ? s : (s + "/");
    }

    private static class Analysis {
        private final Map<String, Long> keep = new HashMap<>();
        private final Set<String> delete = new TreeSet<>(new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                int res = s2.length() - s1.length();
                if (res == 0) {
                    res = s2.compareTo(s1);
                }
                return res;
            }
        });

        public Analysis(Reader sync) throws IOException {
            try (final BufferedReader in = new BufferedReader(sync)) {
                String line;
                while ((line = in.readLine()) != null) {
                    keep.put(line.substring(17), Long.parseLong(line.substring(0, 16), 16));
                }
            }
        }

        public boolean willKeepFile(String localDir, String name) throws IOException {
            final File file = new File(localDir, name);
            final Long hash = keep.get(name);
            if (!file.exists() || hash == null || hash != adler(file)) {
                keep.remove(name);
                delete.add(name);
                return false;
            }
            return true;
        }

        private long adler(File file) throws IOException {
            if (file.isDirectory()) {
                return 0;
            }
            try (final InputStream in = new FileInputStream(file)) {
                final byte[] bytes = new byte[(int) file.length()];
                in.read(bytes);
                final Adler32 adler = new Adler32();
                adler.update(bytes);
                return adler.getValue();
            }
        }

        public void addDirToDelete(String name) {
            keep.remove(name);
            delete.add(withSlash(name));
        }

        public boolean shouldCopy(String localDir, String name) throws IOException {
            final boolean shouldCopy = !keep.containsKey(name);
            if (shouldCopy) {
                final File file = new File(localDir, name);
                keep.put(name, adler(file));
            }
            return shouldCopy;
        }

        public Collection<String> getDeletes() {
            return delete;
        }

        public void saveState(Writer sync) throws IOException {
            try (final BufferedWriter out = new BufferedWriter(sync)) {
                for (Map.Entry<String, Long> entry : keep.entrySet()) {
                    out.write(String.format("%016x %s", entry.getValue(), entry.getKey()));
                    out.newLine();
                }
            }
        }
    }
}
