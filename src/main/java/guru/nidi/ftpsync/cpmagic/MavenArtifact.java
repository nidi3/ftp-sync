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
        final File file = CpMagicUtils.tempFile("repository/" + filename());
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            doDownloadManually(file);
        }
        return file;
    }

    private void doDownloadManually(File dest) {
        try {
            final URL url = new URL("http://search.maven.org/remotecontent?filepath=" + filename());
            CpMagicUtils.copy(url.openStream(), new FileOutputStream(dest));
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
        CpMagicUtils.execute(new ProcessBuilder(args), "mvn terminated with code ");
    }

    private String filename() {
        final String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version;
        final String name = artifactId + "-" + version + ".jar";
        return path + "/" + name;
    }

    @Override
    public String toString() {
        return "MavenArtifact{" +
                groupId + ':' + artifactId + ':' + version +
                '}';
    }
}
