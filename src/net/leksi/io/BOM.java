/*
 * class net.leksi.io.BOM
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

/**
 *
 * Tests BOM (Byte Order Mark) of data from {@code InputStream} and 
 * returns the charset name. The {@code InputStream} after that has BOM 
 * skipped (except the case UTF-7, when the BOM should be decoded first). It is 
 * assumed that {@code InputStream} supports mark.
 * 
 * @author Alexey Zakharov &lt;leksi@leksi.net&gt;.
 */
public class BOM {
    
    private static final String[] ENCODINGS = new String[]{ 
        /* 0 */ null, /* 1 */ "UTF-32BE", /* 2 */ "UTF-16LE", 
        /* 3 */ "UTF-32LE", /* 4 */ "UTF-EBCDIC", /* 5 */ "GB18030", 
        /* 6 */ "UTF-8", /* 7 */ "SCSU", /* 8 */ "UTF-7", /* 9 */ "UTF-1", 
        /* 10 */ "BOCU-1", /* 11 */ "UTF-16BE"
    };
    private static final int[][] SEQUENCES = new int[][]{ 
        /* 0 */ new int[]{}, /* 1  */ new int[]{0, 0, 0xFE, 0xFF}, 
        /* 2 */ new int[]{0xFF, 0xFE}, /* 3 */ new int[]{0, 0}, 
        /* 4 */ new int[]{0xDD, 0x73, 0x66, 0x73}, 
        /* 5 */ new int[]{0x84, 0x31, 0x95, 0x33}, 
        /* 6 */ new int[]{0xEF, 0xBB, 0xBF}, /* 7 */ new int[]{0xE, 0xFE, 0xFF}, 
        /* 8 */ new int[]{0x2B, 0x2F, 0x76}, /* 9 */ new int[]{0x38}, 
        /* 10 */ new int[]{0x2D}, /* 11 */ new int[]{0x39}, 
        /* 12 */ new int[]{0x2B}, /* 13 */ new int[]{0x2F}, 
        /* 14 */ new int[]{0xF7, 0x64, 0x4C}, 
        /* 15 */ new int[]{0xFB, 0xEE, 0x28}, /* 16 */ new int[]{0xFE, 0xFF}};
    private static final int[] ENDS = new int[]{0, 1, 2, 3, 4, 5, 6, 7, -1, 8, 
        8, 8, 8, 8, 9, 10, 11};
    private static final int[][] FOLLOWS = new int[][]{ 
        /* 0 */ new int[]{1, 2, 3, 4, 5, 6, 7, 8, 14, 15, 16}, 
        /* 1 */ null, /* 2 */ new int[]{3}, /* 3 */ null, /* 4 */ null, 
        /* 5 */ null, /* 6 */ null, /* 7 */ null, 
        /* 8 */ new int[]{9, 11, 12, 13}, /* 9 */ new int[]{10}, 
        /* 10 */ null, /* 11 */ null, /* 12 */ null, /* 13 */ null, 
        /* 14 */ null, /* 15 */ null, /* 16 */ null};
    private static final int READLIMIT = 6;
    
    private final ArrayList<Integer> stack = new ArrayList<>();
    private final ArrayList<Boolean> sequenceTested = new ArrayList<>();
    private final ArrayList<Integer> followsPosition = new ArrayList<>();
    private final ArrayList<Integer> bufferOffset = new ArrayList<>();
    
    private int numRead = 0;
    private final byte[] buf = new byte[READLIMIT];

    private void push(final int entry, final int offset) {
        stack.add(entry);
        sequenceTested.add(false);
        followsPosition.add(0);
        bufferOffset.add(offset);
    }

    private int topNode() {
        return stack.get(stack.size() - 1);
    }

    private boolean topSequenceTested() {
        return sequenceTested.get(stack.size() - 1);
    }

    private void topSequenceTested(final boolean value) {
        sequenceTested.set(stack.size() - 1, value);
    }

    private int topFollowsPosition(final boolean postincrement) {
        int res = followsPosition.get(stack.size() - 1);
        if (postincrement) {
            followsPosition.set(stack.size() - 1, res + 1);
        }
        return res;
    }

    private int topBufferRead() {
        int offset = bufferOffset.get(stack.size() - 1);
        int res = offset < numRead ? (int) (buf[offset++] & 0xFF) : -1;
        bufferOffset.set(stack.size() - 1, offset);
        return res;
    }

    private int topBufferOffset() {
        return bufferOffset.get(stack.size() - 1);
    }

    private void pop() {
        int position = stack.size() - 1;
        sequenceTested.remove(position);
        followsPosition.remove(position);
        bufferOffset.remove(position);
        stack.remove(position);
    }

    /**
     * Tests BOM (Byte Order Mark) of data from {@code InputStream} and 
     * returns the charset name. The {@code InputStream} after that has BOM 
     * skipped (except the case UTF-7, when the BOM should be decoded first).
     * 
     * @param input {@code InputStream} to test
     * @return the charset name or null if there is no known BOM
     * @throws IOException re-throws stream's IOException
     */
    public String test(final InputStream input) throws IOException {
        String res = null;
        int resultingOffset = 0;
        input.mark(READLIMIT);
        numRead = input.read(buf);
        push(0, 0);
        while (true) {
            boolean success = true;
            int top = topNode();
            if (!topSequenceTested()) {
                int[] sequence = SEQUENCES[top];
                for (int i = 0; i < sequence.length; i++) {
                    int b = topBufferRead();
                    if (b < 0) {
                        success = false;
                        break;
                    }
                    if (((int) sequence[i] & 0xFF) != b) {
                        success = false;
                        break;
                    }
                }
                topSequenceTested(true);
            }
            if (success) {
                int[] follows = FOLLOWS[top];
                if (follows != null && topFollowsPosition(false) < follows.length) {
                    push(follows[topFollowsPosition(true)], topBufferOffset());
                } else {
                    if (ENDS[top] != -1) {
                        res = ENCODINGS[ENDS[top]];
                        if (!"UTF-7".equals(res)) {
                            resultingOffset = topBufferOffset();
                        }
                        break;
                    }
                    success = false;
                }
            }
            if (!success) {
                pop();
            }
        }
        input.reset();
        if (resultingOffset > 0) {
            input.skip(resultingOffset);
        }
        return res;
    }
    
}
