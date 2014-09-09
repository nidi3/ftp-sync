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
package guru.nidi.ftpsync.cpmagic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 *
 */
public class JarLocalClasspathEnhancer extends AbstractClasspathEnhancer {

    public JarLocalClasspathEnhancer(Class<?> appClass) {
        super(appClass);
    }

    public void enhanceClassLoader() {
        final RuntimeContext runtimeContext = runtimeContext();
        final File appClassContainer = runtimeContext.appClassContainer(this);
        switch (runtimeContext) {
            case FILE:
                enhanceWithFile(appClassContainer);
                break;
            case JAR:
                enhanceWithJar(appClassContainer);
                break;
        }
    }

    private void enhanceWithFile(File basedir) {
        for (File file : basedir.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".jar")) {
                try {
                    enhanceClassLoader(file.toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void enhanceWithJar(File jar) {
        final File dir = CpMagicUtils.tempFile(getAppClass().getName());
        dir.mkdirs();
        try (final JarFile jarFile = new JarFile(jar)) {
            final Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry jarEntry = entries.nextElement();
                if (!jarEntry.isDirectory() && jarEntry.getName().endsWith(".jar")) {
                    final File temp = new File(dir, jarEntry.getName());
                    CpMagicUtils.copy(jarFile.getInputStream(jarEntry), new FileOutputStream(temp));
                    enhanceClassLoader(temp.toURI().toURL());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not handle jar file", e);
        }
    }


}
