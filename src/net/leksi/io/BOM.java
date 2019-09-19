/*
 * net.leksi.io.BOM
 * 
 * v.0.0.1
 * 
 * 28-08-2019
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
import java.util.ArrayDeque;

/**
 * Tests BOM (Byte Order Mark) of data from {@code BranchInputSource} and 
 * returns the charset name. The {@code BranchInputSource} after that has BOM 
 * skipped (except the case UTF-7, when the BOM should be decoded first)
 * 
 * @author Alexey Zakharov &lt;leksi@leksi.net&gt;
 * 
 */
public class BOM {
    
    /**
     * The {@code BOM} is static
     */
    private BOM() { }
    
    /**
     * Auxiliary class represents grammar node
     */
    static class Node {
        int[] sequence = null;
        Node[] choice = null;
        /**
         * The charset name
         */
        String selector = null;
        Node(final int[] seq, final int numChoice, final String selector) {
            this.sequence = seq;
            if(numChoice > 0) {
                choice = new Node[numChoice];
            }
            this.selector = selector;
        }
        
//        @Override
//        public String toString() {
//            return getClass().getSimpleName() + "(" + selector + ")" + (sequence != null ? Arrays.stream(sequence).mapToObj(i -> String.format("0x%02X", i)).collect(Collectors.joining(",", "[", "]")) : "");
//        }
    }
    
    /**
     * Contains current status of the node
     */
    static class StackEntry {
        /**
         * The branch of the input stream
         */
        BranchInputStream stream; 
        Node node;
        /**
         * The position in the {@code choice} array currently tested
         */
        int choicePosition = 0;
        /**
         * Flag signaling if the sequence was already tested
         */
        boolean wasSequenceTested = false;
        
        StackEntry(final BranchInputStream stream, final Node node) {
            this.stream = stream;
            this.node = node;
        }
    }
    /**
     * The root node of the grammar tree
     */
    static Node root = new Node(null, 10, null);
    
    static {
        /*
         * Build grammar tree
         */
        root.choice[0] = new Node(new int[]{0, 0, 0xFE, 0xFF}, 0, "UTF-32BE");
        root.choice[1] = new Node(new int[]{0xFF, 0xFE}, 1, "UTF-16LE");
        root.choice[1].choice[0] = new Node(new int[]{0, 0}, 0, "UTF-32LE");
        root.choice[2] = new Node(new int[]{0xDD, 0x73, 0x66, 0x73}, 0, 
                "UTF-EBCDIC");
        root.choice[3] = new Node(new int[]{0x84, 0x31, 0x95, 0x33}, 0, 
                "GB18030");
        root.choice[4] = new Node(new int[]{0xEF, 0xBB, 0xBF}, 0, "UTF-8");
        root.choice[5] = new Node(new int[]{0xE, 0xFE, 0xFF}, 0, "SCSU");
        root.choice[6] = new Node(new int[]{0x2B, 0x2F, 0x76}, 4, null);
        root.choice[6].choice[0] = new Node(new int[]{0x38}, 1, "UTF-7");
        root.choice[6].choice[0].choice[0] = new Node(new int[]{0x2D}, 0, 
                "UTF-7");
        root.choice[6].choice[1] = new Node(new int[]{0x39}, 0, "UTF-7");
        root.choice[6].choice[2] = new Node(new int[]{0x2B}, 0, "UTF-7");
        root.choice[6].choice[3] = new Node(new int[]{0x2F}, 0, "UTF-7");
        root.choice[7] = new Node(new int[]{0xF7, 0x64, 0x4C}, 0, "UTF-1");
        root.choice[8] = new Node(new int[]{0xFB, 0xEE, 0x28}, 0, "BOCU-1");
        root.choice[9] = new Node(new int[]{0xFE, 0xFF}, 0, "UTF-16BE");
    }
    /**
     * Tests BOM (Byte Order Mark) of data from {@code BranchInputSource} and 
     * returns the charset name. The {@code BranchInputSource} after that has BOM 
     * skipped (except the case UTF-7, when the BOM should be decoded first).
     * 
     * @param stream {@code BranchInputSource} to test
     * @return the charset name or null if there is no known BOM
     * @throws IOException re-throws stream's IOException
     */
    static public String test(final BranchInputStream stream)
            throws IOException {
        String res = null;

        ArrayDeque<StackEntry> stack = new ArrayDeque<>();
        stack.push(new StackEntry(stream, root));
        while(stack.size() > 0) {
            StackEntry top = stack.peek();
            boolean success = true;
            if(!top.wasSequenceTested) {
                if(top.node.sequence != null) {
                    for(int i = 0 ; i < top.node.sequence.length; i++) {
                        int b = top.stream.read();
                        if(b < 0) {
                            success = false;
                            break;
                        }
                        if(((int)top.node.sequence[i] & 0xFF) != b) {
                            success = false;
                            break;
                        }
                    }
                }
                top.wasSequenceTested = true;
            }
            if(success) {
                if(top.node.choice != null && 
                        top.choicePosition < top.node.choice.length) {
                    stack.push(new StackEntry(top.stream.branch(1)[0], 
                            top.node.choice[top.choicePosition]));
                    top.choicePosition++;
                } else {
                    if(top.node.selector != null) {
                        if("UTF-7".equals(top.node.selector)) {
                            stream.closeOthers();
                        } else {
                            top.stream.closeOthers();
                        }
                        res = top.node.selector;
                        break;
                    }
                    success = false;
                }
            }
            if(!success) {
                if(stack.size() > 1) {
                    top.stream.close();
                }
                stack.pop();
            }
        }
        return res;
    }
    
}
