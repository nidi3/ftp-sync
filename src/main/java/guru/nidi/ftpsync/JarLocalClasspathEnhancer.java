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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 *
 */
public class JarLocalClasspathEnhancer extends AbstractClasspathEnhancer {

    public JarLocalClasspathEnhancer() {
    }

    public void enhanceClassLoader() {
        enhanceClassLoader(contextClassLoader());
    }

    public void enhanceClassLoader(ClassLoader classLoader) {
        final String name = getClass().getName().replace('.', '/') + ".class";
        final URL resource = classLoader.getResource(name);
        if (resource == null) {
            throw new RuntimeException("Cannot find myself");
        }
        final String file = resource.getFile();
        switch (resource.getProtocol()) {
            case "file":
                final String basedir = file.substring(0, file.length() - name.length() - 1);
                final int pos = basedir.lastIndexOf('/');
                enhanceWithFile(classLoader, basedir.substring(0, pos));
                break;
            case "jar":
                if (!file.startsWith("file:")) {
                    throw new RuntimeException("Unknown jar protocol " + file);
                }
                final String jar = file.substring(5, file.length() - name.length() - 2);
                enhanceWithJar(classLoader, jar);
                break;
            default:
                throw new RuntimeException("Unknown protocol " + resource.getProtocol());
        }
    }

    private void enhanceWithFile(ClassLoader classLoader, String basedir) {
        for (File file : new File(basedir).listFiles()) {
            if (file.isFile() && file.getName().endsWith(".jar")) {
                try {
                    enhanceClassLoader(classLoader, file.toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void enhanceWithJar(ClassLoader classLoader, String jar) {
        try (final JarFile jarFile = new JarFile(jar)) {
            final Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry jarEntry = entries.nextElement();
                if (!jarEntry.isDirectory() && jarEntry.getName().endsWith(".jar")) {
                    final File temp = new File(System.getProperty("java.io.tmpdir"), jarEntry.getName());
                    copy(jarFile.getInputStream(jarEntry), new FileOutputStream(temp));
                    enhanceClassLoader(classLoader, temp.toURI().toURL());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not handle jar file", e);
        }
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
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
