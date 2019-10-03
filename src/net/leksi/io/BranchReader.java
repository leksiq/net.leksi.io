/*
 * net.leksi.io.BranchReader
 * 
 * v.0.0.1
 * 
 * 23-08-2019
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
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The class {@code BranchReader} is for different consumers to 
 * independently read the same {@code Reader}.
 *
 * @author Alexey Zakharov &lt;leksi@leksi.net&gt;
 * @since JDK1.8
 */
abstract public class BranchReader extends Reader {
    
    static final private int DEFAULT_CHUNK_SIZE = 0x1000;
    
    /**
     * Returns an array of {@code count} new branches {@code BranchReader}
     * which can be read from the current position of the parent's 
     * {@code BranchReader}.
     * 
     * @param count a number of new branches
     * @return an array of new branches
     * @throws IOException if it is closed
     */
    abstract public BranchReader[] branch(final int count) throws IOException;
    
    /**
     * Returns a boolean value meaning if the 
     * {@code BranchReader} is closed.
     * 
     * @return   a boolean value meaning if the 
     *           {@code BranchReader} is closed
     */
    abstract public boolean isClosed();
    
    /**
     * Returns array of not closed branches
     * @return array of not closed branches
     */
    abstract public BranchReader[] getBranches();
    
    /**
     * Closes all branches except this
     * @return boolean indicating the opertion was successful
     * @throws java.io.IOException in  the case of error
     */
    abstract public boolean closeOthers() throws IOException;
    
    /**
     * Returns historical name or null if closed or not supported at 
     * underlying {@code Reader}.
     * @return String Historical name or null if closed or not supported at 
     * underlying {@code Reader}.
     */
    abstract public String getEncoding();
    
    /**
     * Returns current line number starting from 1.
     * @return current line number
     */
    abstract public int getLine();
    
    /**
     * Returns current char position in line starting from 1. If surrogate 
     * symbols present, columns in line should be calculated.
     * @return current char position in line 
     */
    abstract public int getCharPositionInLine();
    
    /**
     * Pushes back an array of characters by copying it to the front of the 
     * pushback buffer.
     * @param cbuf Character array
     * @throws java.io.IOException If there is insufficient room in the 
     *                              pushback buffer, or if some other I/O error 
     *                              occurs
     */
    public void unread(char[] cbuf) throws IOException {
        unread(cbuf, 0, cbuf.length);
    }

    /**
     * Pushes back a portion of an array of characters by copying it to the 
     * front of the pushback buffer.
     * @param cbuf Character array
     * @param off Offset of first character to push back
     * @param len Number of characters to push back
     * @throws java.io.IOException If there is insufficient room in the 
     *                              pushback buffer, or if some other I/O error 
     *                              occurs
     */
    abstract public void unread(char[] cbuf, int off, int len) 
            throws IOException;

    /**
     * Pushes back a single character by copying it to the front of the pushback 
     * buffer.    
     * @param c The int value representing a character to be pushed back
     * @throws java.io.IOException If there is insufficient room in the 
     *                              pushback buffer, or if some other I/O error 
     *                              occurs
     */
    abstract public void unread(int c) throws IOException;
    
    /**
     * A factory method for creation of an {@code BranchReader} object of 
     * the concrete implementation based on the openned underlying
     * {@code Reader}. The  reading is possible from the current position of the 
     * {@code source}.
     * 
     * @param source the preliminary openned {@code Reader}
     * @return root {@code BranchReader} object
     */
    static public BranchReader create(final Reader source) {
        return new Root(source).root();
    }
    
    /**
     * A factory method for creation of an {@code BranchReader} object of 
     * the concrete implementation based on the openned underlying
     * {@code Reader}. The  reading is possible from the current position of the 
     * {@code source}.
     * 
     * @param source the preliminary openned {@code Reader}
     * @param chunkSize defines size of byte chunk instead of default one.
     *                  chunkSize &lt;= 0 means default.
     * @return root {@code BranchReader} object
     */
    static public BranchReader create(final Reader source, 
            final int chunkSize) {
        return new Root(source, chunkSize).root();
    }
    
    /**
     * A factory method for creation of an {@code BranchReader} object of 
     * the concrete implementation based on the openned underlying
     * {@code InputStream}. Reads and parses BOM if presents. Overwrites charset
     * defined by BOM with {@code encoding} if {@code overwriteBOM} is 
     * {@code true}. Applies {@code encoding} charser if there is no BOM.
     * 
     * @param source   the underlying {@code InputStream}.
     * @param encoding the charset name to apply if there is no BOM or 
     *                 {@code overwriteBOM} is {@code true}.
     * @param overwriteBOM the flag signaling whether to apply {@code encoding}
     *                     charset regarless if there is BOM.
     * @param chunkSize defines size of byte chunk instead of default one.
     *                  chunkSize &lt;= 0 means default.
     * @return root {@code BranchReader} object
     * @throws IOException underlying IOException
     */
    static public BranchReader create(final InputStream source, 
            final String encoding, final boolean overwriteBOM,
            final int chunkSize) throws IOException {
        BranchInputStream stream = BranchInputStream.create(source, chunkSize);
        String charsetName = BOM.test(stream);
        InputStream input = stream.getBranches()[0];
        if(encoding != null) {
            if(overwriteBOM) {
                charsetName = encoding;
            }
        }
        if("UTF-7".equals(charsetName)) {
            input = new UTF7InputStream(input);
            charsetName = "UTF-16BE";
        }
        if(charsetName == null) {
            charsetName = "UTF-8";
        }
        return BranchReader.create(new InputStreamReader(input, charsetName),
                chunkSize);
    }
    
    /**
     * A factory method for creation of an {@code BranchReader} object of 
     * the concrete implementation based on the openned underlying
     * {@code InputStream}. Reads and parses BOM if presents. Applies 
     * UTF-8 charser if there is no BOM.
     * 
     * @param source   the underlying {@code InputStream}.
     * @return root {@code BranchReader} object
     * @throws IOException underlying IOException
     */
    static public BranchReader create(final InputStream source) throws IOException {
        return BranchReader.create(source, null, false, 0);
    }
    
    /**
     * The class {@code Chunk} is an auxiliary class to support a singly 
     * linked list of data pieces read from the underlying {@code Reader}. 
     * <p>
     * In order to use memory sparingly we point the end chunk which is a point 
     * of grouth and every branch points its currently read chunk. Thus, chunks 
     * read by all branches are objects for the GC.
     */
    private static class Chunk {
        /**
         * The actual chunk length.
         */
        private int length = 0;
        /**
         * The {@code char} array to contain the data chunk.
         */
        private char buffer[] = null;
        /**
         * The pointer to the next chunk.
         */
        private Chunk next = null;
        /**
         * An offset of the chunk's starting position from the whole data's one.
         */
        private long offset = 0;

        /**
         * Creates a chunk of predefined size.
         * @param chunkSize size of memory in {@code char}s to allocate for the 
         * chunk.
         */
        private Chunk(final int chunkSize) {
            buffer = new char[chunkSize];
        }
    }

    /**
     * The class {@code Root} is an infrastructure holder for the <i>tree</i> of 
     * {@code BranchReader} objects.
     */
    private static class Root {

        private int chunkSize = DEFAULT_CHUNK_SIZE;
    
        /**
         * The underlying {@code Reader}.
         */
        private Reader source = null;
        /**
         * The flag indicating that the underlying {@code Reader} is fully read.
         */
        private boolean isSourceEnded = false;
        /**
         * The list of all branches of the tree.
         */
        private final HashMap<Long, Branch> branches = new HashMap<>();
        /**
         * The last chunk at the singly linked list of data pieces read from the 
         * underlying {@code Reader}.
         */
        private Chunk endChunk = new Chunk(0);
        /**
         * Generates ids for branches 
         */
        private final AtomicLong idGenerator = new AtomicLong(0);
        /**
         * Historical name of the encoding or null
         */
        private String encodingName = null;
        /**
         * New line char (\n or \r, what met first)
         */
        private char newLine = 0;
        
        /**
         * Stores columns counts at lines
         */
        private Map<Integer, Integer> columnsCounts = 
                Collections.synchronizedMap(new HashMap<>());
       
        /**
         * The class {@code Branch} is a concrete implementation of the abstract
         * {@code BranchReader}.
         */
        private class Branch extends BranchReader {
            /**
             * The current offset relative to the issue of the 
             * underlying {@code Reader}.
             */
            private long position = 0;
            /**
             * The chunk currently being read.
             */
            private Chunk chunk = null;
            /**
             * Is the {@code Branch} closed.
             */
            private AtomicBoolean isClosed = new AtomicBoolean(false);
            /**
             * {@code Branch}'s id to distinguish thorously
             */
            private long id = idGenerator.incrementAndGet();
            /**
             * Pushback buffer
             */
            private StringBuffer pushbackBuffer = null;
            /**
             *  Line of text
             */
            private int line = 1;
            /**
             *  Column of text
             */
            private int charPositionInLine = 1;

            /**
             * Creates a branch with a parent if it is given
             * 
             * @param parent    a parent branch of the new branch or 
             *                  {@code null} in the case of root
             */
            private Branch(final Branch parent) {
                if (parent != null) { 
                    /*
                     * if the parent is given, just clone it
                     */
                    position = parent.position;
                    chunk = parent.chunk;
                    line = parent.line;
                    charPositionInLine = parent.charPositionInLine;
                    if(parent.pushbackBuffer != null) {
                        pushbackBuffer = new StringBuffer(parent.pushbackBuffer);
                    }
                }
            }

            @Override
            public BranchReader[] branch(final int count) throws IOException {
                synchronized(Root.this) {
                    BranchReader[] res;

                    if (isClosed()) {
                        throw new IOException("Cannot branch closed reader");
                    }
                    res = new BranchReader[count];
                    for (int i = 0; i < count; i++) {
                        res[i] = new Branch(this);
                        branches.put(((Branch) res[i]).id, (Branch) res[i]);
                    }
                    return res;
                }
            }

            @Override
            public boolean isClosed() {
                return isClosed.get();
            }
            
            private void calculateLineAndColumnPushback(final char c) {
                if(c == '\n' || c == '\r') {
                    if(c == Root.this.newLine) {
                        line--;
                        charPositionInLine = columnsCounts.get(line);
                    }
                } else {
                    if(charPositionInLine > 1) {
                        charPositionInLine--;
                    }
                }
            }
            
            private void calculateLineAndColumn(final char[] cbuf, 
                    final int off, final int len) {
                for(int i = 0; i < len; i++) {
                    if(cbuf[off + i] == '\n' || cbuf[off + i] == '\r') {
                        if(Root.this.newLine == 0) {
                            synchronized(Root.this) {
                                if (Root.this.newLine == 0) {
                                    Root.this.newLine = cbuf[off + i];
                                }
                            }
                        }
                        if(cbuf[off + i] == Root.this.newLine) {
                            columnsCounts.putIfAbsent(line, charPositionInLine);
                            line++;
                            charPositionInLine = 1;
                        }
                    } else {
                        charPositionInLine++;
                    }
                }
            }

            @Override
            public int read(final char[] cbuf, 
                    final int off, final int len) throws IOException {
                int res = -1;              // returned result
                int readCount = 0;          // cumulative count of chars copied 
                                            // from (probably) several chunks
                                            
                if(!isClosed.get()) {
                    boolean canRead = true;
                    if(len == 0) {
                        return 0;
                    }
                    if(pushbackBuffer != null && pushbackBuffer.length() > 0) {
                        if(len <= pushbackBuffer.length()) {
                            pushbackBuffer.getChars(0, len, cbuf, off);
                            readCount += len;
                        } else {
                            pushbackBuffer.getChars(0, pushbackBuffer.length(), cbuf, off);
                            readCount += pushbackBuffer.length();
                        }
                        pushbackBuffer.delete(0, readCount);
                        calculateLineAndColumn(cbuf, off, readCount);
                    }
                    if (position + len - readCount > endChunk.offset + endChunk.length) {
                        synchronized(Root.this) {
                            long dataLength = endChunk.offset + endChunk.length;
                            if (position + len - readCount > dataLength && !isSourceEnded) {
                                /*
                                 * Should read from the underlying {@code
                                 * Reader}.
                                 */
                                int leftReadCount = (int) (position + len  - 
                                        readCount - dataLength);
                                int leftChunkLength = endChunk.buffer.length - 
                                        endChunk.length;
                                while (leftReadCount > 0) {
                                    if (leftChunkLength == 0) {
                                        /*
                                         * Allocate new chunk and add it to list
                                         */
                                        endChunk.next = new Chunk(leftReadCount);
                                        endChunk.next.offset = dataLength;
                                        endChunk = endChunk.next;
                                        leftChunkLength = endChunk.buffer.length;
                                    }

                                    int n = source.read(endChunk.buffer,
                                            endChunk.length,
                                            Math.min(leftReadCount,
                                                    leftChunkLength));
                                    if (n <= 0) {
                                        isSourceEnded = true;
                                        break;
                                    }
                                    endChunk.length += n;
                                    leftReadCount -= n;
                                    leftChunkLength -= n;
                                }
                            }
                        }
                    }
                    while (!isClosed.get() && readCount < len) {
                        int from;
                        int n;
                        if (position >= chunk.offset + chunk.length) {
                            canRead = false;
                            if(chunk.next != null) {        
                                chunk = chunk.next;
                                canRead = true;
                            }
                        }
                        if(canRead) {
                            from = (int)(position - chunk.offset);
                            n = Math.min(chunk.length - from, len - readCount);
                            System.arraycopy(chunk.buffer, from, cbuf, off + readCount, n);
                            calculateLineAndColumn(chunk.buffer, from, n);
                            position += n;
                            readCount += n;
                        } else {
                            break;
                        }
                    }
                    res = (readCount > 0 && !isClosed.get() ? readCount : -1);
                }
                return res;
            }
            
            @Override
            public void close() throws IOException {
                synchronized (Root.this) {
                    branches.remove(id);
                    isClosed.set(true);
                    if (branches.isEmpty() && source != null) {
                        source.close();
                        source = null;
                    }
                }
            }

            @Override
            public BranchReader[] getBranches() {
                synchronized(Root.this) {
                    if(branches.isEmpty()) {
                        return new BranchReader[]{};
                    }
                    return branches.values().stream().toArray(BranchReader[]::new);
                }
            }

            @Override
            public boolean closeOthers() throws IOException {
                synchronized(Root.this) {
                    if(isClosed()) {
                        return false;
                    }
                    ArrayList<Branch> toClose = new ArrayList<>();
                    for(long key: branches.keySet()) {
                        if(key != id) {
                            toClose.add(branches.get(key));
                        }
                    }
                    for(Branch branch: toClose) {
                        branch.close();
                    }
                }
                return true;
            }

            @Override
            public String getEncoding() {
                synchronized(Root.this) {
                    return encodingName;
                }
            }

            @Override
            public void unread(char[] cbuf, int off, int len) throws IOException {
                if(pushbackBuffer == null) {
                    pushbackBuffer = new StringBuffer(chunkSize);
                }
                pushbackBuffer.insert(0, cbuf, off, len);
                for(int i = len - 1; i >= 0; i--) {
                    calculateLineAndColumnPushback(cbuf[off + i]);
                }
            }

            @Override
            public void unread(int c) throws IOException {
                if(pushbackBuffer == null) {
                    pushbackBuffer = new StringBuffer(chunkSize);
                }
                pushbackBuffer.insert(0, (char)c);
                calculateLineAndColumnPushback((char)c);
            }

            @Override
            public int getLine() {
                return line;
            }

            @Override
            public int getCharPositionInLine() {
                return charPositionInLine;
                        
            }

        }
        
        /**
         * Creates new {@code Root} object with an underlying {@code Reader}.
         * 
         * @param source the underlying {@code InputStream}.
         * @param chunkSize defines size of byte chunk instead of default one.
         */
        private Root(final Reader source, final int chunkSize) {
            this.chunkSize = chunkSize;
            this.source = source;
            init();
        }

        /**
         * Creates new {@code Root} object with an underlying {@code Reader}.
         * 
         * @param source the underlying {@code Reader}.
         */
        private Root(final Reader source) {
            this.source = source;
            init();
        }
        
        private void init() {
            if (this.source instanceof InputStreamReader) {
                encodingName = ((InputStreamReader) this.source).getEncoding();
            } else {
                try {
                    Method method = this.source.getClass().getMethod(
                            "getEncoding", new Class[]{});
                    encodingName = (String) method.invoke(this.source, new Object[]{});
                } catch (Exception ex) {
                }
            }
        }

        /**
         * Creates and returns the root branch
         * 
         * @return the root branch
         */
        private Branch root() {
            Branch root = new Branch(null);
            root.chunk = endChunk;
            branches.put(root.id, root);
            return root;
        }
    }
}
