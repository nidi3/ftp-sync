package guru.nidi.ftpsync;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 *
 */
public class MavenRepoClasspathEnhancer {
    private final String mavenRepo;
    private final Method addURL;

    public MavenRepoClasspathEnhancer() {
        mavenRepo = findLocalRepo();
        try {
            addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public void enhanceClassLoader(ClassLoader classLoader, String groupId, String artifactId, String version) {
        if (!(classLoader instanceof URLClassLoader)) {
            throw new RuntimeException("Cannot change classpath dynamically");
        }
        URLClassLoader ucl = (URLClassLoader) classLoader;
        try {
            final String filename = mavenRepo + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
            addURL.invoke(ucl, new URL("file:" + filename));
        } catch (Exception e) {
            throw new RuntimeException("Cannot change classpath dynamically", e);
        }
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
