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
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 *
 */
public abstract class AbstractClasspathEnhancer {
    private final Class<?> appClass;
    private final Method addURL;

    public AbstractClasspathEnhancer(Class<?> appClass) {
        try {
            this.appClass = appClass;
            addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private URL appClassUrl() {
        final String name = appClass.getName().replace('.', '/') + ".class";
        final URL resource = appClass.getClassLoader().getResource(name);
        if (resource == null) {
            throw new RuntimeException("Cannot find application class");
        }
        return resource;
    }

    protected Class<?> getAppClass() {
        return appClass;
    }

    private int appClassLen() {
        return appClass.getName().length() + 6;
    }

    protected enum RuntimeContext {
        FILE {
            @Override
            public File appClassContainer(AbstractClasspathEnhancer enhancer) {
                final String file = enhancer.appClassUrl().getFile();
                final String basedir = file.substring(0, file.length() - enhancer.appClassLen() - 1);
                final int pos = basedir.lastIndexOf('/');
                return new File(basedir.substring(0, pos));
            }
        },
        JAR {
            @Override
            public File appClassContainer(AbstractClasspathEnhancer enhancer) {
                final String file = enhancer.appClassUrl().getFile();
                if (!file.startsWith("file:")) {
                    throw new RuntimeException("Unknown jar protocol " + file);
                }
                return new File(file.substring(5, file.length() - enhancer.appClassLen() - 2));
            }
        };

        public abstract File appClassContainer(AbstractClasspathEnhancer enhancer);
    }

    protected RuntimeContext runtimeContext() {
        final URL url = appClassUrl();
        switch (url.getProtocol()) {
            case "file":
                return RuntimeContext.FILE;
            case "jar":
                return RuntimeContext.JAR;
            default:
                throw new RuntimeException("Unknown protocol " + url.getProtocol());
        }
    }

    public void enhanceClassLoader(String url) {
        try {
            enhanceClassLoader(new URL(url));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Illegal URL", e);
        }
    }

    public void enhanceClassLoader(URL url) {
        final ClassLoader classLoader = appClass.getClassLoader();
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


}
