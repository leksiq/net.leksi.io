/*
 * net.leksi.io.UTF7InputStream
 * 
 * v.0.0.1
 * 
 * 11-09-2019
 *
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
import java.util.ArrayList;
import java.util.Arrays;

/**
 * The class {@code UTF7InputStream} reads UTF-7 encoded data and gives it on
 * UTF-16BE encoding.
 * 
 * @author Alexey Zakharov &lt;leksi@leksi.net&gt;
 * @since JDK1.8
 */
public class UTF7InputStream extends InputStream {

    /**
     * The underlying {@code InputSource} object.
     */
    private InputStream source = null;
    /**
     * The auxiliary buffer for decoding base64 chunks.
     */
    private final byte[] buffer = new byte[6];
    /**
     * The cache for bytes which cannot be returned at the moment.
     */
    private final ArrayList<Byte> cache = new ArrayList<>();
    /**
     * The position for the next reading from the {@code buffer}. If it equals 
     * to the {@code buffer}'s length, the next data from the {@code source} 
     * needed.
     */
    private int tail = 0;
    /**
     * The flag signalling if the {@code source} ended.
     */
    private boolean isSourceEnded = false;
    /**
     * The length of data in the {@code buffer}.
     */
    private int bufferSize = 0;
    /**
     * The flag signalling if the current position of the {@code source} is in
     * base64 chunk.
     */
    private boolean inBase64 = false;
    /**
     * The current position of the output data.
     */
    private long outPosition = 0;
    
    /**
     * Returns the base64 code of the input symbol
     * @param ch input symbol
     * @return the base64 code of the input symbol or -1 if it is not a base64
     * alphabet member.
     */
    private byte getBase64Code(final int ch) {
        byte res = -1;
        if(ch > 0x40 && ch < 0x5B) {
            res = (byte)(ch - 65);
        } else if(ch > 0x60 && ch < 0x7B) {
            res = (byte)(ch - 71);
        } else if(ch > 0x2F && ch < 0x3A) {
            res = (byte)(ch + 4);
        } else if(ch == '+') {
            res = 62;
        } else if(ch == '/') {
            res = 63;
        }
        return res;    
    }

    /**
     * Creates {@code UTF7InputStream} object over the given {@code InputStream}.
     * @param source the given {@code InputStream}.
     */
    public UTF7InputStream(final InputStream source) {
        this.source = source;
    }
    
    /**
     * The auxiliary method for not encoded symbol to expand it to 16 bits
     * @param b not encoded symbol to expand 
     * @return 0 as the most significant byte
     */
    private int to16(final int b) {
        cache.add((byte)b);
        return 0;
    }
    
    @Override
    public int read() throws IOException {
        int res = -1; // return value
        if(tail == bufferSize) { // more data needed
            if(!cache.isEmpty()) { // read from cahce
                res = (int)cache.remove(0) & 0xFF;
            } else if(!isSourceEnded) { // read the source
                boolean plus = false; // the '+' was just read
                bufferSize = 0; // initialize
                tail = 0;       // buffer
                int numSextets = 0;   // initialize number of read sextets
                while (true) { // loop unknown times until can break
                    int b = source.read();
                    if (b == -1) { // the source ended, no more data 
                                   // available
                        isSourceEnded = true;
                        break;
                    }
                    if (!inBase64) { 
                        if (!plus && b == (int) '+') {
                            plus = true;
                            continue;   // as '+' just read
                                        // see the next
                        } else if (b == (int) '-') {
                            if (plus) { // "+-" is '+' 
                                res = to16((int) '+');
                                break;
                            }
                            res = to16(b); // '-' itself
                            break;
                        } else if (!plus) {
                            res = to16(b); // any nit encoded symbol
                            break;
                        }
                        inBase64 = true;
                    }
                    // we are at base64 chunk until
                    // get 8 sextets or get not base64 alphabet member
                    byte code = getBase64Code((char) b);
                    if (code != -1) {
                        switch (numSextets) {
                            case 0:
                                Arrays.fill(buffer, (byte) 0);
                                buffer[0] = (byte) ((int) code << 2 & 
                                        0b11111100);
                                break;
                            case 1:
                                buffer[0] |= (byte) ((int) code >> 4 & 
                                        0b00000011);
                                buffer[1] |= (byte) (((int) code & 
                                        0b00001111) << 4);
                                break;
                            case 2:
                                buffer[1] |= (byte) ((int) code >> 2 & 
                                        0b00001111);
                                buffer[2] |= (byte) ((int) code << 6 & 
                                        0b11000000);
                                break;
                            case 3:
                                buffer[2] |= (byte) ((int) code & 
                                        0b00111111);
                                break;
                            case 4:
                                buffer[3] = (byte) ((int) code << 2 & 
                                        0b11111100);
                                break;
                            case 5:
                                buffer[3] |= (byte) ((int) code >> 4 & 
                                        0b00000011);
                                buffer[4] |= (byte) (((int) code & 
                                        0b00001111) << 4);
                                break;
                            case 6:
                                buffer[4] |= (byte) ((int) code >> 2 & 
                                        0b00001111);
                                buffer[5] |= (byte) ((int) code << 6 & 
                                        0b11000000);
                                break;
                            default:
                                buffer[5] |= (byte) ((int) code & 
                                        0b00111111);
                                break;
                        }
                        numSextets++;
                        if (numSextets == 8) {
                            // got 8 sextets
                            break;
                        }
                    } else {
                        // got not base64 alphabet member
                        if (b != (int) '-') {
                            // omit '-' or cache other
                            cache.add((byte) 0);
                            cache.add((byte) b);
                        }
                        inBase64 = false;
                        break; // leave base64 chunk
                    }
                }
                if(res == -1) { // if didn't get not encoded symbol 
                                // then processed buffer
                    if (numSextets > 0) {
                        // omit extra bits
                        bufferSize = 2 * ((numSextets * 3) / 8);
                        // check if the extra bits are zeros
                        for(int i = bufferSize; i < buffer.length; i++) {
                            if(buffer[i] != 0) {
                                throw new IOException("Invalid code!");
                            }
                        }
                    }
                }
            }
        }
        if(tail < bufferSize) { // if there are data in the buffer
                                // return it
            res = (int)buffer[tail++] & 0xFF;
        }
        if(res != -1) {
            // check the BOM 0xFF 0xFE and cut it
            if(outPosition == 0 && res == 0xFE && ((int)buffer[tail] & 0xFF) == 0xFF) {
                outPosition = 2;
                tail++;
                res = read();
            } else {
                outPosition++;
            }
        }
        return res;
    }
}

