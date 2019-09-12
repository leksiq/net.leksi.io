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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Alexey Zakharov <leksi@leksi.net>
 */
public class UTF7InputStreamTest {
    
    public UTF7InputStreamTest() {
    }
    
    /**
     * Test of read method, of class UTF7InputStream.
     * 
     * ta
     */
    @Test
//    public void testRead1() throws Exception {}
    public void testRead() throws Exception {
        System.out.println("read");
        
        try(
                UTF7InputStream utf7is = new UTF7InputStream(new ByteArrayInputStream(new byte[]{0x2B, 0x2F, 0x75, 0x38, 0x2D}));
                ) {
            byte[] buffer = new byte[0x1000];
            int n;
            while((n = utf7is.read(buffer)) >= 0) {
            }
        }

        try(
                UTF7InputStream utf7is = new UTF7InputStream(new ByteArrayInputStream(new byte[]{0x2B, 0x2B, 0x76, 0x38, 0x2D}));
                ) {
            byte[] buffer = new byte[0x1000];
            int n;
            while((n = utf7is.read(buffer)) >= 0) {
            }
        }


        try(
                UTF7InputStream utf7is = new UTF7InputStream(new ByteArrayInputStream(new byte[]{0x2B, 0x2F, 0x76, 0x38, 0x2F, 0x2D}));
                ) {
            byte[] buffer = new byte[0x1000];
            int n;
            while((n = utf7is.read(buffer)) >= 0) {
            }
            assertTrue(false);
        } catch(IOException ex) {
            assertEquals("Invalid code!", ex.getMessage());
        }

        StringBuilder expectedText = new StringBuilder();
        StringBuilder resultText = new StringBuilder();
                
        try(InputStream is = getClass().getClassLoader().getResourceAsStream("2-utf-8.txt");) {
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
        try(
                UTF7InputStream utf7is = new UTF7InputStream(getClass().getClassLoader().getResourceAsStream("2-utf-7.txt"));
                InputStreamReader isr = new InputStreamReader(utf7is, Charset.forName("UTF-16BE"));
                ) {
            char[] buffer = new char[0x1000];
            int n;
            while((n = isr.read(buffer)) >= 0) {
                resultText.append(buffer, 0, n);
            }
        }
        assertEquals(expectedText.toString(), resultText.toString());
    }
    
}
