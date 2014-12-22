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

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class LocalFileSystem extends FileSystemBase {
    public LocalFileSystem(String basedir) {
        super(basedir);
    }

    private File file(String name) {
        return new File(expand(name));
    }

    @Override
    public void deleteFile(String name) throws IOException {
        file(name).delete();
    }

    @Override
    public void deleteDirectory(String name) throws IOException {
        file(name).delete();
    }

    @Override
    public void putFile(File local, String dest) throws IOException {
        FsUtils.copy(new FileInputStream(local), new FileOutputStream(expand(dest)));
    }

    @Override
    public void getFile(File local, String dest) throws IOException {
        FsUtils.copy(new FileInputStream(expand(dest)), new FileOutputStream(local));
    }

    @Override
    public void createDirectory(String name) throws IOException {
        file(name).mkdirs();
    }

    @Override
    public List<AbstractFile> listFiles(String dir, AbstractFileFilter filter) throws IOException {
        final File[] files = file(dir).listFiles(new FileFilterImpl(filter));
        List<AbstractFile> res = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                res.add(new AbstractFileImpl(file));
            }
        }
        return res;
    }

    private static class FileFilterImpl implements FileFilter {
        private final AbstractFileFilter filter;

        private FileFilterImpl(AbstractFileFilter filter) {
            this.filter = filter;
        }

        @Override
        public boolean accept(File pathname) {
            return filter.accept(new AbstractFileImpl(pathname));
        }
    }

    private static class AbstractFileImpl implements AbstractFile {
        private final File file;

        private AbstractFileImpl(File file) {
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

        @Override
        public File asFile() {
            return file;
        }
    }

    @Override
    public void close() throws IOException {
    }
}
