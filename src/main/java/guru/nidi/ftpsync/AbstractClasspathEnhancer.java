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

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 *
 */
public abstract class AbstractClasspathEnhancer {
    private final Method addURL;

    public AbstractClasspathEnhancer() {
        try {
            addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public void enhanceClassLoader(ClassLoader classLoader, String url) {
        try {
            enhanceClassLoader(classLoader, new URL(url));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Illegal URL", e);
        }
    }

    public void enhanceClassLoader(ClassLoader classLoader, URL url) {
        if (!(classLoader instanceof URLClassLoader)) {
            throw new RuntimeException("Cannot change classpath dynamically");
        }
        URLClassLoader ucl = (URLClassLoader) classLoader;
        try {
            addURL.invoke(ucl, url);
        } catch (Exception e) {
            throw new RuntimeException("Cannot change classpath dynamically", e);
        }
    }

    public ClassLoader contextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    protected static void copy(InputStream in, OutputStream out) throws IOException {
        try (final BufferedInputStream bin = new BufferedInputStream(in);
             final BufferedOutputStream bout = new BufferedOutputStream(out)) {
            final byte[] buf = new byte[1000];
            int read;
            while ((read = bin.read(buf)) > 0) {
                bout.write(buf, 0, read);
            }
        }
    }
}
