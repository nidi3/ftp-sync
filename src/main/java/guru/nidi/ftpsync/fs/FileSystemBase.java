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

/**
 *
 */
public abstract class FileSystemBase implements FileSystem {
    protected final String basedir;

    public FileSystemBase() {
        this("");
    }

    public FileSystemBase(String basedir) {
        this.basedir = basedir.endsWith("/") ? basedir.substring(0, basedir.length() - 1) : basedir;
    }

    protected String expand(String s) {
        return basedir + (s.startsWith("/") ? "" : "/") + s;
    }

    @Override
    public String getBasedir() {
        return basedir;
    }
}
