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

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class MavenRepoClasspathEnhancer extends AbstractClasspathEnhancer {
    private static final Pattern DEPENDENCY = Pattern.compile("(\\S+):(\\S+):(\\S+):(\\S+):(\\S+)");
    private final String mavenRepo;

    public MavenRepoClasspathEnhancer(Class<?> appClass) {
        super(appClass);
        mavenRepo = findLocalRepo();
    }

    public void enhanceClassLoader() {
        final RuntimeContext runtimeContext = runtimeContext();
        final File appClassContainer = runtimeContext.appClassContainer(this);
        switch (runtimeContext) {
            case FILE:
                enhanceFromFilePom(appClassContainer);
                break;
            case JAR:
                enhanceFromJarPom(appClassContainer);
                break;
        }
    }

    private void enhanceFromJarPom(File appClassContainer) {
        try (final JarFile jarFile = new JarFile(appClassContainer)) {
            final Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry jarEntry = entries.nextElement();
                final String name = jarEntry.getName();
                if (!jarEntry.isDirectory() && name.startsWith("META-INF/maven/") && name.endsWith("/pom.xml")) {
                    enhanceFromPom(jarFile.getInputStream(jarEntry), appClassContainer);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not open pom.xml in jar", e);
        }
    }

    private void enhanceFromFilePom(File appClassContainer) {
        try {
            final File file = new File(appClassContainer.getParentFile(), "pom.xml");
            enhanceFromPom(new FileInputStream(file), file);
        } catch (IOException e) {
            throw new RuntimeException("pom.xml not found", e);
        }
    }

    private void enhanceFromPom(InputStream pom, File source) throws IOException {
        final File dir = CpMagicUtils.tempFile(getAppClass().getName());
        dir.mkdirs();
        final File dependencyFile = new File(dir, "dependencies");
        enhanceWithDependencies(
                (dependencyFile.exists() && source.lastModified() == dependencyFile.lastModified())
                        ? loadDependencies(dependencyFile)
                        : calcDependencies(pom, source, dir, dependencyFile)
        );
    }

    private void enhanceWithDependencies(String dependencies) {
        final Matcher matcher = DEPENDENCY.matcher(dependencies);
        while (matcher.find()) {
            final String scope = matcher.group(5);
            if (!scope.equals("test") && !scope.equals("provided")) {
                enhanceClassLoader(matcher.group(1), matcher.group(2), matcher.group(4));
            }
        }
    }

    private String loadDependencies(File dependencies) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        CpMagicUtils.copy(new FileInputStream(dependencies), out);
        return new String(out.toByteArray());
    }

    private String calcDependencies(InputStream pom, File source, File dir, File dependencies) throws IOException {
        CpMagicUtils.copy(pom, new FileOutputStream(new File(dir, "pom.xml")));
        final String output = CpMagicUtils.execute(
                new ProcessBuilder("mvn", "org.apache.maven.plugins:maven-dependency-plugin:2.8:list").directory(dir),
                "mvn terminated with code ");
        CpMagicUtils.copy(new ByteArrayInputStream(output.getBytes()), new FileOutputStream(dependencies));
        dependencies.setLastModified(source.lastModified());
        return output;
    }

    public void enhanceClassLoader(String groupId, String artifactId, String version) {
        final MavenArtifact gav = new MavenArtifact(groupId, artifactId, version);
        final File file = mavenRepo != null
                ? gav.downloadWithMaven(mavenRepo)
                : gav.downloadManually();
        enhanceClassLoader("file:" + file.getAbsolutePath());
    }

    private String findLocalRepo() {
        String loc = findLocalRepo(System.getProperty("user.home") + "/.m2");
        if (loc == null || loc.length() == 0) {
            loc = findLocalRepo(System.getenv("M2_HOME") + "/conf");
        }
        return loc;
    }

    private String findLocalRepo(String settingsLocation) {
        final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(false);
        final DocumentBuilder documentBuilder;
        try {
            documentBuilder = builderFactory.newDocumentBuilder();
            final Document settings = documentBuilder.parse(settingsLocation + "/settings.xml");

            final XPathFactory xPathfactory = XPathFactory.newInstance();
            final XPath xpath = xPathfactory.newXPath();
            final XPathExpression expr = xpath.compile("/settings/localRepository/text()");
            return (String) expr.evaluate(settings, XPathConstants.STRING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
