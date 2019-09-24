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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;

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
    String resource = "1.txt";                      // Source text file
    int initial_readers_count = 123;                // Initial branches number
    int n_repeats = 100;                            // 
    Map<Integer, String> strings = Collections.synchronizedMap(new HashMap<>());
    List<Thread> threads = Collections.synchronizedList(new ArrayList<>());
    int closedOthersId = -1;

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testCloseOthers() throws Exception {
        System.out.println("testCloseOthers");
        text.delete(0, text.length());
        /**
         * Read source file into refernce text
         */
        try(
            Reader source = new InputStreamReader(getClass().getClassLoader().getResourceAsStream("1.zip"), "UTF-8");
        ) {
            char buf[] = new char[0x1000];
            int n = 0;
            while((n = source.read(buf)) > 0) {
                text.append(buf, 0, n);
            }
        }
        try(
            Reader source = new InputStreamReader(getClass().getClassLoader().getResourceAsStream("1.zip"), "UTF-8");
            BranchReader result = BranchReader.create(source);
        ) {
            BranchReader[] br = result.branch(2);
            char buf1[] = new char[text.length()];
            int n = result.read(buf1);
            assertEquals(text.length(), n);
            
            Thread t1 = new Thread(() -> {
                try {
                    int res = br[0].read(buf1);
                } catch (Exception ex) {
                    fail(ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\n    " + Arrays.stream(ex.getStackTrace()).map(e -> e.toString()).collect(Collectors.joining("\n    ")));
                }
            });
            
            Thread t2 = new Thread(() -> {
                try {
                    boolean res = br[1].closeOthers();
                    assertTrue(res);
                } catch (Exception ex) {
                    fail(ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\n    " + Arrays.stream(ex.getStackTrace()).map(e -> e.toString()).collect(Collectors.joining("\n    ")));
                }
            });
            t1.start();
            t2.start();
            if(t1.isAlive()) {
                t1.join();
            }
            if(t2.isAlive()) {
                t2.join();
            }
        }

    }
    @Test
//    public void testCreate1() throws Exception {}
    public void testCreate() throws Exception {
        System.out.println("testCreate");
        text.delete(0, text.length());
        /**
         * Read source file into refernce text
         */
        try(
            Reader source = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(resource), "UTF-8");
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
                Reader source = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(resource), "UTF-8");
                BranchReader result = BranchReader.create(source);
            ) {
                assertEquals(Charset.forName("UTF-8"), Charset.forName(result.getEncoding()));
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
            int numReads = 0;
            boolean closedOthers = false;
            try {
                if(scenario != 1 && !br.isClosed()) {
                    assertEquals(0, br.read(buf, 0, 0));
                }
                while(true) {
                    numReads++;
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
                    if(numReads == (id % 2 == 0 ?  2 : 4)) {
                        br.unread(buf);
                    } else {
                        if(numReads == (id % 2 == 0 ?  4 : 2)) {
                            br.unread(buf[n - 1]);
                            sb.append(buf, 0, n - 1);
                        } else {
                            sb.append(buf, 0, n);
                        }
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

    /**
     * Test of create method, of class BranchReader.
     */
    @Test
    public void testCreate_InputStream_1arg_UTF7() throws Exception {
        System.out.println("testCreate_InputStream_1arg_UTF7");
        StringBuilder expectedText = new StringBuilder();
        StringBuilder resultText = new StringBuilder();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("2-utf-8.txt");) {
            //skip BOM
            is.read();
            is.read();
            is.read();
            try (Reader source = new InputStreamReader(is, Charset.forName("UTF-8"));) {
                char[] buffer = new char[0x1000];
                int n;
                while ((n = source.read(buffer)) >= 0) {
                    expectedText.append(buffer, 0, n);
                }
            }
        }
        try (BranchReader reader = BranchReader.create(getClass().getClassLoader().getResourceAsStream("2-utf-7.txt"));) {
            char[] buffer = new char[0x1000];
            int n;
            while ((n = reader.read(buffer)) >= 0) {
                resultText.append(buffer, 0, n);
            }
        }
        assertEquals(expectedText.toString(), resultText.toString());
    }

    /**
     * Test of create method, of class BranchReader.
     */
    @Test
    public void testCreate_InputStream_1arg_UTF8() throws Exception {
        System.out.println("testCreate_InputStream_1arg_UTF8");
        StringBuilder expectedText = new StringBuilder();
        StringBuilder resultText = new StringBuilder();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("2-utf-8.txt");) {
            //skip BOM
            is.read();
            is.read();
            is.read();
            try (Reader source = new InputStreamReader(is, Charset.forName("UTF-8"));) {
                char[] buffer = new char[0x1000];
                int n;
                while ((n = source.read(buffer)) >= 0) {
                    expectedText.append(buffer, 0, n);
                }
            }
        }
        try (
                InputStream is = getClass().getClassLoader().getResourceAsStream("2-utf-8.txt");
                ) {
            //skip BOM
            is.read();
            is.read();
            is.read();
            try(BranchReader reader = BranchReader.create(is);) {
                char[] buffer = new char[0x1000];
                int n;
                while ((n = reader.read(buffer)) >= 0) {
                    resultText.append(buffer, 0, n);
                }
            }
        }
        assertEquals(expectedText.toString(), resultText.toString());

        resultText.delete(0, resultText.length());
        try (BranchReader reader = BranchReader.create(getClass().getClassLoader().getResourceAsStream("2-utf-8.txt"));) {
            char[] buffer = new char[0x1000];
            int n;
            while ((n = reader.read(buffer)) >= 0) {
                resultText.append(buffer, 0, n);
            }
        }
        assertEquals(expectedText.toString(), resultText.toString());
    }

    /**
     * Test of create method, of class BranchReader.
     */
    @Test
    public void testCreate_InputStream_3rg_UTF16BE() throws Exception {
        System.out.println("testCreate_InputStream_3rg_UTF16BE");
        StringBuilder expectedText = new StringBuilder();
        StringBuilder resultText = new StringBuilder();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("2-utf-8.txt");) {
            //skip BOM
            is.read();
            is.read();
            is.read();
            try (Reader source = new InputStreamReader(is, Charset.forName("UTF-8"));) {
                char[] buffer = new char[0x1000];
                int n;
                while ((n = source.read(buffer)) >= 0) {
                    expectedText.append(buffer, 0, n);
                }
            }
        }
        try(InputStream is = getClass().getClassLoader().getResourceAsStream("2-utf-16be.txt");) {
            //skip BOM
            is.read();
            is.read();
            try (BranchReader reader = BranchReader.create(is, "UTF-16BE", true, 0);) {
                char[] buffer = new char[0x1000];
                int n;
                while ((n = reader.read(buffer)) >= 0) {
                    resultText.append(buffer, 0, n);
                }
            }
        }
        assertEquals(expectedText.toString(), resultText.toString());

        resultText.delete(0, resultText.length());
        try(InputStream is = getClass().getClassLoader().getResourceAsStream("2-utf-16be.txt");) {
            try (BranchReader reader = BranchReader.create(is, "UTF-16LE", false, 0);) {
                char[] buffer = new char[0x1000];
                int n;
                while ((n = reader.read(buffer)) >= 0) {
                    resultText.append(buffer, 0, n);
                }
            }
        }
        assertEquals(expectedText.toString(), resultText.toString());
    }
    
    class Reader1 extends Reader {

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void close() throws IOException {
        }
        
        public String getEncoding() {
            return "UTF8";
        }
        
    }
    
    @Test
    public void testGetEncoding() throws Exception {
        System.out.println("testGetEncoding");
        try(BranchReader reader = BranchReader.create(new Reader1());) {
            assertEquals(Charset.forName("UTF-8"), Charset.forName(reader.getEncoding()));
        }
    }

}
