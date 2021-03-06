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
        enhancer.enhanceClassLoader();
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

    private final FileSystem remoteFileSystem;
    private final FileSystem localFileSystem;
    private final boolean forceRemoteAnalysis;
    private final File syncFile;

    public FtpSync(Config config) throws IOException {
        remoteFileSystem = config.isSecure()
                ? new SftpFileSystem(config.getRemoteDir(), config)
                : new FtpFileSystem(config.getRemoteDir(), config);
        localFileSystem = new LocalFileSystem(config.getLocalDir());
        forceRemoteAnalysis = config.isForceRemoteAnalysis();
        final File local = new File(config.getLocalDir());
        syncFile = new File(local.getParentFile(),
                sanitizeForFilename(config.getHost() + "-" + config.getRemoteDir() + "-" + local.getName() + ".sync"));
        if (!syncFile.exists()) {
            syncFile.createNewFile();
        }
    }

    private String sanitizeForFilename(String s) {
        final StringBuilder res = new StringBuilder(s);
        for (int i = 0; i < res.length(); i++) {
            final char c = res.charAt(i);
            final boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || ".".indexOf(c) >= 0;
            if (!ok) {
                res.setCharAt(i, '-');
            }
        }
        return res.toString();
    }

    @Override
    public void close() throws IOException {
        remoteFileSystem.close();
    }

    public static void main(String[] args) throws IOException {
        try (final FtpSync sync = new FtpSync(new Config(args))) {
            sync.sync();
        }
    }

    public void sync() throws IOException {
        try {
            remoteFileSystem.createDirectory("");
        } catch (IOException e) {
            //ignore
        }
        final Analysis analysis = new Analysis(new InputStreamReader(new FileInputStream(syncFile), "utf-8"));
        final FileSystem fsToAnalyze = (syncFile.length() == 0 || forceRemoteAnalysis)
                ? remoteFileSystem
                : localFileSystem;
        analyze(fsToAnalyze, "/", analysis);
        delete(analysis);
        copy("/", analysis);
        analysis.saveState(new OutputStreamWriter(new FileOutputStream(syncFile), "utf-8"));
    }

    public boolean analyze(FileSystem fileSystem, final String dir, final Analysis analysis) throws IOException {
        final String target = fileSystem instanceof LocalFileSystem ? "local" : "remote";
        System.out.println("Analyzing " + target + ": " + dir);
        final boolean[] keepAny = new boolean[]{false};
        Utils.doProgressively(fileSystem.listFiles(dir, SELECT_FILES), new Utils.ProgressWorker<AbstractFile>() {
            @Override
            public String itemName(AbstractFile item) {
                return item.getName();
            }

            @Override
            public void processItem(AbstractFile item) throws Exception {
                keepAny[0] |= analysis.willKeepFile(localFileSystem.getBasedir(), withSlash(dir) + item.getName());
            }
        });
        for (AbstractFile sub : fileSystem.listFiles(dir, SELECT_DIRS)) {
            keepAny[0] |= analyze(fileSystem, withSlash(dir) + sub.getName(), analysis);
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
                    remoteFileSystem.deleteDirectory(item);
                } else {
                    remoteFileSystem.deleteFile(item);
                }
            }
        });
    }

    public void copy(final String dir, final Analysis analysis) throws IOException {
        System.out.println("Copying to remote: " + dir);

        if (analysis.shouldCopy(localFileSystem.getBasedir(), dir)) {
            remoteFileSystem.createDirectory(dir);
        }

        Utils.doProgressively(localFileSystem.listFiles(dir, SELECT_FILES), new Utils.ProgressWorker<AbstractFile>() {
            @Override
            public String itemName(AbstractFile item) {
                return item.getName();
            }

            @Override
            public void processItem(AbstractFile item) throws IOException {
                final String fullname = withSlash(dir) + item.getName();
                if (analysis.shouldCopy(localFileSystem.getBasedir(), fullname)) {
                    remoteFileSystem.putFile(item.asFile(), fullname);
                }
            }
        });

        for (AbstractFile sub : localFileSystem.listFiles(dir, SELECT_DIRS)) {
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
