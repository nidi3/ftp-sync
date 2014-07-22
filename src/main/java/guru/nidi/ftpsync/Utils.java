package guru.nidi.ftpsync;

import java.io.*;

/**
 *
 */
class Utils {
    private Utils() {
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        try (final BufferedInputStream bin = new BufferedInputStream(in);
             final BufferedOutputStream bout = new BufferedOutputStream(out)) {
            final byte[] buf = new byte[1000];
            int read;
            while ((read = bin.read(buf)) > 0) {
                bout.write(buf, 0, read);
            }
        }
    }

    static File tempFile(String name) {
        return new File(System.getProperty("java.io.tmpdir"), name);
    }

    static String execute(ProcessBuilder builder, String errorMsg) {
        try {
            final Process proc = builder.redirectErrorStream(true).start();
            final int result = proc.waitFor();
            final StringBuilder res = new StringBuilder();
            try (final BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                while (in.ready()) {
                    res.append(in.readLine()).append("\n");
                }
            }
            if (result != 0) {
                throw new RuntimeException(errorMsg + result + ":\n" + builder.command() + "\n" + res.toString());
            }
            return res.toString();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
