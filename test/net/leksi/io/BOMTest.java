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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Alexey Zakharov <leksi@leksi.net>
 */
public class BOMTest {
    
    public BOMTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of test method, of class BOM.
     */
    @Test
    public void testTest() throws Exception {
        System.out.println("test");
        byte[][] input = new byte[][]{
            new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF, 0},
            new byte[]{(byte)0xFE, (byte)0xFF, 0},
            new byte[]{(byte)0xFF, (byte)0xFE, 0},
            new byte[]{0, 0, (byte)0xFE, (byte)0xFF, 0},
            new byte[]{(byte)0xFF, (byte)0xFE, 0, 0, 0},
            new byte[]{(byte)0x2B, (byte)0x2F, (byte)0x76, (byte)0x38},
            new byte[]{(byte)0x2B, (byte)0x2F, (byte)0x76, (byte)0x39},
            new byte[]{(byte)0x2B, (byte)0x2F, (byte)0x76, (byte)0x2B},
            new byte[]{(byte)0x2B, (byte)0x2F, (byte)0x76, (byte)0x2F},
            new byte[]{(byte)0x2B, (byte)0x2F, (byte)0x76, (byte)0x38, (byte)0x2D},
            new byte[]{(byte)0xF7, (byte)0x64, (byte)0x4C, 0},
            new byte[]{(byte)0xDD, (byte)0x73, (byte)0x66, (byte)0x73, 0},
            new byte[]{(byte)0x0E, (byte)0xFE, (byte)0xFF, 0},
            new byte[]{(byte)0xFB, (byte)0xEE, (byte)0x28, 0},
            new byte[]{(byte)0x84, (byte)0x31, (byte)0x95, (byte)0x33, 0},
            new byte[]{(byte)0x1, (byte)0x2, (byte)0x3, (byte)0x4, 0},
            new byte[]{(byte)0x2B, (byte)0x2F, (byte)0x76, (byte)0x37}
        };
        String[] expResults = new String[]{
            "UTF-8", "UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE",
            "UTF-7", "UTF-7", "UTF-7", "UTF-7", "UTF-7", "UTF-1", "UTF-EBCDIC",
            "SCSU", "BOCU-1", "GB18030"
        };
        byte[] buf = new byte[32];
        for(int i = 0; i < input.length; i++) {
            try(BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(input[i]));) {
                String result = new BOM().test(stream);
                int n = stream.read(buf);
                byte[] b = new byte[n];
                System.arraycopy(buf, 0, b, 0, n);
                if(i < expResults.length) {
                    assertEquals(expResults[i], result);
                } else {
                    assertEquals(null, result);
                }
                if(i >= 5 && i <= 9 || i >= expResults.length) {
                    assertArrayEquals(input[i], b);
                } else {
                    assertArrayEquals(new byte[]{0}, b);
                }
            }
        }
    }
    
}
