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

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.File;

/**
 *
 */
public class MavenRepoClasspathEnhancer extends AbstractClasspathEnhancer {
    private final String mavenRepo;

    public MavenRepoClasspathEnhancer() {
        mavenRepo = findLocalRepo();
    }

    public void enhanceClassLoader(String groupId, String artifactId, String version) {
        enhanceClassLoader(contextClassLoader(), groupId, artifactId, version);
    }

    public void enhanceClassLoader(ClassLoader classLoader, String groupId, String artifactId, String version) {
        final MavenArtifact gav = new MavenArtifact(groupId, artifactId, version);
        final File file = mavenRepo != null
                ? gav.downloadWithMaven(mavenRepo)
                : gav.downloadManually();
        enhanceClassLoader(classLoader, "file:" + file.getAbsolutePath());
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
