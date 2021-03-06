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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
public class BranchInputStreamTest {
    
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

    String resource = "1.zip";                      // Source text file
    int initial_readers_count = 123;                // Initial branches number
    int n_repeats = 100;                            // 
    Map<Integer, byte[]> arrays = Collections.synchronizedMap(new HashMap<>());
    List<Thread> threads = Collections.synchronizedList(new ArrayList<>());
    int closedOthersId = -1;
    int referenceLength;

    /**
     * 
     * @param a
     * @param b
     * @param c
     * @return 
     */
    private boolean byteArraysEqual(final byte[] a, final byte[] b, 
            final byte[] c) {
        boolean res = true;
        if(a.length != b.length + c.length) {
            res = false;
        } else {
            for (int i = 0; i < Math.max(b.length, c.length); i++) {
                if (i < b.length) {
                    if(a[i] != b[i]) {
                        res = false;
                        break;
                    }
                }
                if (i < c.length) {
                    if(a[i + b.length] != c[i]) {
                        res = false;
                        break;
                    }
                }
            }
        }
        return res;
    }
    
    @Test
//    public void testCreate1() throws Exception {}
    public void testCreate() throws Exception {
        ByteArrayOutputStream referenceData = new ByteArrayOutputStream();
        byte[] referenceArr;
        byte[] empty = new byte[]{};
        /**
         * Read source file into refernce text
         */
        try(
            InputStream source = getClass().getClassLoader().
                    getResourceAsStream(resource);
        ) {
            byte buf[] = new byte[0x1000];
            int n;
            while((n = source.read(buf)) > 0) {
                referenceData.write(buf, 0, n);
            }
        }
        referenceArr = referenceData.toByteArray();
        referenceLength = referenceArr.length;
        
        for(int i = 0; i < n_repeats; i++) {
//            System.err.println("repeat: " + i);
            /**
             * Initialize environment
             */
            int scenario = (i % 10 == 0 ? 1 : 0);
            arrays.clear();
            threads.clear();
            gen_id.set(0);
            try(
                InputStream source = getClass().getClassLoader().
                        getResourceAsStream(resource);
                BranchInputStream result = (i % 3 == 2 ? 
                        BranchInputStream.create(source, 
                                (int)Math.ceil(0x1000 * (1 + Math.random()))) : 
                        BranchInputStream.create(source));
            ) {
                /**
                 * Collect initial branches
                 */
                ArrayList<BranchInputStream> list = new ArrayList<>();
                list.add(result);
                list.addAll(Arrays.stream(result.
                        branch(initial_readers_count - 1)).
                        collect(Collectors.toList()));
                assertEquals(initial_readers_count, 
                        result.getBranches().length);
                /**
                 * Collect initial threads
                 */
                Thread[] initial_threads = list.stream().
                        map(br -> new_thread(br, scenario)).
                        toArray(Thread[]::new);
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
                    assertEquals(String.valueOf(i), 0, result.getBranches().length);
                    /**
                     * Test 1)
                     */
                    assertEquals(
                            initial_readers_count + initial_readers_count / 2, 
                            arrays.size());

                    for(int k = 0; k < initial_readers_count; k++) {
                        if(k % 2 == 0) {
                            /**
                             * Test 2)
                             */
                            assertTrue(byteArraysEqual(referenceArr, 
                                    arrays.get(k), 
                                    arrays.get(initial_readers_count)));
                        } else {
                            /**
                             * Test 3)
                             */
                            assertTrue(byteArraysEqual(referenceArr, 
                                    arrays.get(k), empty));
                        }
                    }
                    /**
                     * Test 4)
                     */
                    for(int k = initial_readers_count + 1; k < arrays.size(); 
                            k++) {
                        assertTrue(byteArraysEqual(arrays.
                                get(initial_readers_count), arrays.get(k), 
                                empty));
                    }
                } else {
                    assertEquals(1, result.getBranches().length);
                    assertTrue(byteArraysEqual(referenceArr, 
                            arrays.get(closedOthersId), empty));
                }
            }
        }
    }
    
    /**
     * ID generator to separate odd and even threads/branches
     */
    AtomicInteger gen_id = new AtomicInteger(0);
    
    private Thread new_thread(final BranchInputStream br, final int scenario) {
        int id = gen_id.getAndIncrement();
        Thread res = new Thread(() -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            /**
             * temporary buffer of random size
             */
            byte buf[] = new byte[(int)Math.ceil(0x800 * (1 + Math.random()))];
            int n = 0;
            /**
             * Rest length before half for initial branches
             */
            int rest_len = id < initial_readers_count || scenario == 1 ? 
                    referenceLength / 2 : -1;
            boolean closedOthers = false;
            try {
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
                        assertTrue(scenario == 1 || id % 2 == 1 || 
                                id >= initial_readers_count);
                        arrays.put(id, baos.toByteArray());
                        if(scenario == 0) {
                            br.close();
                        }
                        break;
                    }
                    baos.write(buf, 0, n);
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
                                arrays.put(id, baos.toByteArray());
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
                        assertTrue(false);
                    } catch(Exception ex1) {
                        /**
                         * can not branch closed branch
                         */
                        assertTrue(ex1 instanceof IOException);
                    }
                }
            } catch (Exception ex) {
                fail(ex.getClass().getSimpleName() + ": " + ex.getMessage() + 
                        "\n    " + Arrays.stream(ex.getStackTrace()).
                                map(e -> e.toString()).
                                collect(Collectors.joining("\n    ")));
            }
        });
        threads.add(res);
        return res;
    }
    
    
}
