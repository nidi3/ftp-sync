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

import java.io.*;

/**
 *
 */
class CpMagicUtils {
    private CpMagicUtils() {
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
