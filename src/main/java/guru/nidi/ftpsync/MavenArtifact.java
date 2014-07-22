package guru.nidi.ftpsync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URL;

/**
*
*/
class MavenArtifact {
    private final String groupId;
    private final String artifactId;
    private final String version;

    public MavenArtifact(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public File downloadManually() {
        final File file = new File(System.getProperty("java.io.tmpdir") + "/repository", filename());
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            doDownloadManually(file);
        }
        return file;
    }

    private void doDownloadManually(File dest) {
        try {
            final URL url = new URL("http://search.maven.org/remotecontent?filepath=" + filename());
            AbstractClasspathEnhancer.copy(url.openStream(), new FileOutputStream(dest));
        } catch (Exception e) {
            throw new RuntimeException("Could not download artifact", e);
        }
    }

    public File downloadWithMaven(String mavenRepo) {
        final File file = new File(mavenRepo, filename());
        if (!file.exists()) {
            doDownloadWithMaven();
        }
        return file;
    }

    private void doDownloadWithMaven() {
        final String[] args = new String[]{"mvn", "org.apache.maven.plugins:maven-dependency-plugin:2.8:get",
                "-DremoteRepositories=central::default::http://repo1.maven.apache.org",
                "-DgroupId=" + groupId, "-DartifactId=" + artifactId, "-Dversion=" + version};
        try {
            final Process proc = new ProcessBuilder(args).redirectErrorStream(true).start();
            final int result = proc.waitFor();
            final StringBuilder res = new StringBuilder();
            try (final BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                while (in.ready()) {
                    res.append(in.readLine()).append("\n");
                }
            }
            if (result != 0) {
                throw new RuntimeException("mvn terminated with code " + result + ": " + res.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String filename() {
        final String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version;
        final String name = artifactId + "-" + version + ".jar";
        return path + "/" + name;
    }
}
