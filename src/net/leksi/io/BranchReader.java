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
import java.util.ArrayList;
import java.util.HashMap;
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
     */
    abstract public boolean closeOthers();
    
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
     * {@code InputStream}. Reads and parses BOM if presents. Overwrites charset
     * defined by BOM with {@code encoding} if {@code overwriteBOM} is 
     * {@code true}. Applies {@code encoding} charser if there is no BOM.
     * 
     * @param source   the underlying {@code InputStream}.
     * @param encoding the charset name to apply if there is no BOM or 
     *                 {@code overwriteBOM} is {@code true}.
     * @param overwriteBOM the flag signaling whether to apply {@code encoding}
     *                     charset regarless if there is BOM.
     * @return root {@code BranchReader} object
     * @throws IOException underlying IOException
     */
    static public BranchReader create(final InputStream source, 
            final String encoding, final boolean overwriteBOM) 
            throws IOException {
        BranchInputStream stream = BranchInputStream.create(source);
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
        return BranchReader.create(new InputStreamReader(input, charsetName));
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
        return BranchReader.create(source, null, false);
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
         * The class {@code Branch} is a concrete implementation of the abstract
         * {@code BranchReader}.
         */
        /**
         * Generates ids for branches 
         */
        private AtomicLong idGenerator = new AtomicLong(0);
        
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
                    if (position + len > endChunk.offset + endChunk.length) {
                        synchronized(Root.this) {
                            long dataLength = endChunk.offset + endChunk.length;
                            if (position + len > dataLength && !isSourceEnded) {
                                /*
                                 * Should read from the underlying {@code
                                 * Reader}.
                                 */
                                int leftReadCount = (int) (position + len - dataLength);
                                /*
                                 * Allocate new chunk and add it to list
                                 */
                                endChunk.next = new Chunk(leftReadCount);
                                endChunk.next.offset = dataLength;
                                endChunk = endChunk.next;

                                while (leftReadCount > 0) {
                                    int n = source.read(endChunk.buffer,
                                            endChunk.length,
                                            Math.min(leftReadCount,
                                                    endChunk.buffer.length
                                                    - endChunk.length));
                                    if (n <= 0) {
                                        isSourceEnded = true;
                                        break;
                                    }
                                    endChunk.length += n;
                                    leftReadCount -= n;
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
            public boolean closeOthers() {
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
                        try {
                            branch.close();
                        } catch (IOException ex) {
                            // Not reacheable as underlying source never
                            // close here
                        }
                    }
                }
                return true;
            }

        }
        
        /**
         * Creates new {@code Root} object with an underlying {@code Reader}.
         * 
         * @param source the underlying {@code Reader}.
         */
        private Root(final Reader source) {
            this.source = source;
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
