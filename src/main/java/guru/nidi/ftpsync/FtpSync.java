package guru.nidi.ftpsync;

import java.io.File;
import java.io.IOException;

/**
 *
 */
public class FtpSync {
    static {
        final MavenRepoClasspathEnhancer enhancer = new MavenRepoClasspathEnhancer();
        final ClassLoader current = Thread.currentThread().getContextClassLoader();
        enhancer.enhanceClassLoader(current, "org.bouncycastle", "bcpkix-jdk15on", "1.50");
        enhancer.enhanceClassLoader(current, "org.bouncycastle", "bcprov-jdk15on", "1.50");
    }

    public void sync(Config config) throws IOException {
        try (final Client client = createClient(config)) {
            deleteDirs(client, config.getRemoteDir());
            copyDirs(client, new File(config.getLocalDir()), config.getRemoteDir());
        }
    }

    public Client createClient(Config config) throws IOException {
        if (config.isSecure()) {
            return new SFtpImpl(config);
        }
        return new FtpImpl(config);
    }

    public void copyDirs(Client client, File sourceDir, String destDir) throws IOException {
        System.out.println("Copying to remote: " + sourceDir);
        final String base = destDir.endsWith("/") ? destDir : (destDir + "/");
        final File[] files = sourceDir.listFiles();
        client.createDirectory(destDir);
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    client.copyFile(file, base + file.getName());
                } else if (file.isDirectory()) {
                    copyDirs(client, file, base + file.getName());
                }
            }
        }
    }

    public void deleteDirs(Client client, String path) throws IOException {
        System.out.println("Deleting remote: " + path);
        final String base = path.endsWith("/") ? path : (path + "/");
        for (Client.RemoteFile file : client.listFiles(path)) {
            final String name = file.getName();
            if (file.isFile()) {
                client.deleteFile(base + name);
            } else if (file.isDirectory() && !name.equals(".") && !name.equals("..")) {
                deleteDirs(client, base + name);
            }
        }
        client.deleteDirectory(path);
    }

    public static void main(String[] args) throws IOException {
        new FtpSync().sync(new Config(args));
    }

}
