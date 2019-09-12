/*
 * The MIT License
 *
 * Copyright 2019 Alexey Zakharov <leksi@leksi.net>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package net.leksi.io;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Alexey Zakharov &lt;leksi@leksi.net&gt;
 */
public class BranchReaderTest {
    
    /**
     * Test of create method, of class BranchReader.
     * 
     * We allocate {@code initial_readers_count} Branches and start each in 
     * a separate thread to read. When half of data read each odd branch give a
     * branch, each even branch closes. After all we test 
     * 1) if the total number of 
     * branches equals to expected;
     * 2) if the text read by each initial even branch 
     * concatenated with text read by first branch after initial equals to 
     * reference text;
     * 3) if the text read by each initial odd branch 
     * equals to reference text;
     * 4) if the texts read by each branch after initials are equal.
     * Also, to ensure the best code covarage, we test some branch closing 
     * deals
     */

    StringBuilder text = new StringBuilder();       // Reference text data
    String resource = "1.zip";                      // Source text file
    int initial_readers_count = 123;                // Initial branches number
    int n_repeats = 100;                            // 
    Map<Integer, String> strings = Collections.synchronizedMap(new HashMap<>());
    List<Thread> threads = Collections.synchronizedList(new ArrayList<>());
    int closedOthersId = -1;

    @Test
//    public void testCreate1() throws Exception {}
    public void testCreate() throws Exception {
        /**
         * Read source file into refernce text
         */
        try(
            Reader source = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(resource));
        ) {
            char buf[] = new char[0x1000];
            int n = 0;
            while((n = source.read(buf)) > 0) {
                text.append(buf, 0, n);
            }
        }
        
        for(int i = 0; i < n_repeats; i++) {
            /**
             * Initialize environment
             */
            int scenario = (i % 10 == 0 ? 1 : 0);
            strings.clear();
            threads.clear();
            gen_id.set(0);
            try(
                Reader source = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(resource));
                BranchReader result = BranchReader.create(source);
            ) {
                /**
                 * Collect initial branches
                 */
                ArrayList<BranchReader> list = new ArrayList<>();
                list.add(result);
                list.addAll(Arrays.stream(result.branch(initial_readers_count - 1)).collect(Collectors.toList()));
                assertEquals(initial_readers_count, result.getBranches().length);
                /**
                 * Collect initial threads
                 */
                Thread[] initial_threads = list.stream().map(br -> new_thread(br, scenario)).toArray(Thread[]::new);
                /**
                 * Start initial threads
                 */
                for(Thread th: initial_threads) {
                    th.start();
                }
                /**
                 * Wait for all therads finish
                 */
                while(threads.size() > 0) {
                    if(threads.get(0).isAlive()) {
                        threads.get(0).join();
                    }
                    threads.remove(0);
                }
                if(scenario == 0) {
                    assertEquals(0, result.getBranches().length);
                    /**
                     * Test 1)
                     */
                    assertEquals(initial_readers_count + initial_readers_count / 2, strings.size());
                    for(int k = 0; k < initial_readers_count; k++) {
                        if(k % 2 == 0) {
                            /**
                             * Test 2)
                             */
                            assertEquals(text.toString(), strings.get(k) + strings.get(initial_readers_count));
                        } else {
                            /**
                             * Test 3)
                             */
                            assertEquals(text.toString(), strings.get(k));
                        }
                    }
                    /**
                     * Test 4)
                     */
                    for(int k = initial_readers_count + 1; k < strings.size(); k++) {
                        assertEquals(strings.get(initial_readers_count), strings.get(k));
                    }
                } else {
                    assertEquals(1, result.getBranches().length);
                    assertEquals(text.toString(), strings.get(closedOthersId));
                }
            }
        }
    }
    
    /**
     * ID generator to separate odd and even threads/branches
     */
    AtomicInteger gen_id = new AtomicInteger(0);
    
    private Thread new_thread(final BranchReader br, final int scenario) {
        int id = gen_id.getAndIncrement();
        Thread res = new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            /**
             * temporary buffer of random size
             */
            char buf[] = new char[(int)Math.ceil(0x800 * (1 + Math.random()))];
            int n = 0;
            /**
             * Rest length before half for initial branches
             */
            int rest_len = id < initial_readers_count || scenario == 1 ? text.length() / 2 : -1;
            boolean closedOthers = false;
            try {
                assertEquals(0, br.read(buf, 0, 0));
                while(true) {
                    if(rest_len > 0 && rest_len < buf.length) {
                        n = br.read(buf, 0, rest_len);
                    } else {
                        n = br.read(buf);
                    }
                    if(n <= 0) {
                        /**
                         * Only odd and out of initial branches could come here
                         */
                        assertTrue(scenario == 1 || id % 2 == 1 || id >= initial_readers_count);
                        strings.put(id, sb.toString());
                        if(scenario == 0) {
                            br.close();
                        }
                        break;
                    }
                    sb.append(buf, 0, n);
                    rest_len -= n;
                    if(scenario == 0) {
                        if(id < initial_readers_count && rest_len == 0) {
                            /**
                             * half of the way
                             */
                            rest_len = -1;
                            if (id % 2 == 0) {
                                /**
                                 * close even
                                 */
                                br.close();
                                strings.put(id, sb.toString());
                                break;
                            } else {
                                /**
                                 * branch odd
                                 */
                                new_thread(br.branch(1)[0], scenario).start();
                            }
                        }
                    } else {
                        if(rest_len == 0) {
                            rest_len = -1;
                            closedOthers = br.closeOthers();
                            assertEquals(1, br.getBranches().length);
                            if(closedOthers) {
                                closedOthersId = id;
                                assertEquals(br, br.getBranches()[0]);
                            }
                        }
                    }
                    /**
                     * let other threads to play
                     */
                    Thread.currentThread().sleep(0, 1);
                }
                /**
                 * test if the branch closed
                 */
                if(!closedOthers) {
                    assertTrue(br.isClosed());
                    assertFalse(br.closeOthers());
                } else {
                    assertTrue(!br.isClosed());
                }
                assertEquals(-1, br.read());
                if(scenario == 0) {
                    try {
                        br.branch(2314);
                        assertTrue(true);
                    } catch(Exception ex1) {
                        /**
                         * can not branch closed branch
                         */
                        assertTrue(ex1 instanceof IOException);
                    }
                }
            } catch (Exception ex) {
                fail(ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\n    " + Arrays.stream(ex.getStackTrace()).map(e -> e.toString()).collect(Collectors.joining("\n    ")));
            }
        });
        threads.add(res);
        return res;
    }
    
    
}
