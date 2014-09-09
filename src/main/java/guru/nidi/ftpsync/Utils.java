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

import java.util.Collection;

/**
 *
 */
class Utils {
    private Utils() {
    }

    interface ProgressWorker<T> {
        String itemName(T item);

        void processItem(T item) throws Exception;
    }

    static <T> void doProgressively(Collection<T> items, ProgressWorker<T> worker) {
        if (items == null || items.size() == 0) {
            return;
        }
        try {
            int count = 0;
            for (T item : items) {
                int rate = (100 * count / items.size());
                String line = "[" + (rate < 10 ? " " : "") + rate + "%] " + worker.itemName(item);
                System.out.print(line);
                count++;
                worker.processItem(item);
                System.out.print("\r" + dup(" ", line.length()) + "\r");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String dup(String c, int count) {
        final StringBuilder s = new StringBuilder(count * c.length());
        for (int i = 0; i < count; i++) {
            s.append(c);
        }
        return s.toString();
    }

}
