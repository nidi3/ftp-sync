package guru.nidi.ftpsync;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 *
 */
public interface Client extends Closeable {
    void deleteFile(String name) throws IOException;

    void deleteDirectory(String name) throws IOException;

    void copyFile(File local, String dest) throws IOException;

    void createDirectory(String name) throws IOException;

    List<RemoteFile> listFiles(String dir) throws IOException;

    interface RemoteFile {
        boolean isFile();

        boolean isDirectory();

        String getName();
    }
}
