/*
 * net.leksi.io.BranchInputStream
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import jdk.nashorn.internal.ir.Symbol;

/**
 * The class {@code BranchInputStream} is for different consumers to 
 * independently read the same {@code InputStream}.
 *
 * @author Alexey Zakharov &lt;leksi@leksi.net&gt;
 * @since JDK1.8
 */
abstract public class BranchInputStream extends InputStream {
    
    static final private int DEFAULT_CHUNK_SIZE = 0x1000;
    
    /**
     * Returns an array of {@code count} new branches {@code BranchInputStream}
     * which can be read from the current position of the parent's 
     * {@code BranchInputStream}.
     * 
     * @param count a number of new branches
     * @return an array of new branches
     * @throws IOException if it is closed
     */
    abstract public BranchInputStream[] branch(final int count) throws IOException;
    
    /**
     * Returns a boolean value meaning if the 
     * {@code BranchInputStream} is closed.
     * 
     * @return   a boolean value meaning if the 
     *           {@code BranchInputStream} is closed
     */
    abstract public boolean isClosed();
    
    /**
     * Returns array of not closed branches
     * @return array of not closed branches
     */
    abstract public BranchInputStream[] getBranches();
    
    /**
     * Closes all branches except this
     * @return boolean indicating the opertion was successful
     */
    abstract public boolean closeOthers();
    
    /**
     * A factory method for creation of an {@code BranchInputStream} object of 
     * the concrete implementation based on the openned underlying
     * {@code InputStream}. The  reading is possible from the current position 
     * of the {@code source}.
     * 
     * @param source the preliminary openned {@code InputStream}
     * @param chunkSize defines size of byte chunk instead of default one.
     * @return root {@code BranchInputStream} object
     */
    static public BranchInputStream create(final InputStream source, 
            final int chunkSize) {
        return new Root(source, chunkSize).root();
    }
    
    /**
     * A factory method for creation of an {@code BranchInputStream} object of 
     * the concrete implementation based on the openned underlying
     * {@code InputStream}. The  reading is possible from the current position 
     * of the {@code source}.
     * 
     * @param source the preliminary openned {@code InputStream}
     * @return root {@code BranchInputStream} object
     */
    static public BranchInputStream create(final InputStream source) {
        return new Root(source).root();
    }
    
    /**
     * The class {@code Chunk} is an auxiliary class to support a singly 
     * linked list of data pieces read from the underlying {@code InputStream}. 
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
        private byte buffer[] = null;
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
            buffer = new byte[chunkSize];
        }
    }

    /**
     * The class {@code Root} is an infrastructure holder for the <i>tree</i> of 
     * {@code BranchInputStream} objects.
     */
    private static class Root {
        
        private int chunkSize = DEFAULT_CHUNK_SIZE;
    
        /**
         * The underlying {@code InputStream}.
         */
        private InputStream source = null;
        /**
         * The flag indicating that the underlying {@code InputStream} is fully 
         * read.
         */
        private boolean isSourceEnded = false;
        /**
         * The list of all branches of the tree.
         */
        private final HashMap<Long, Branch> branches = new HashMap<>();
        /**
         * The last chunk at the singly linked list of data pieces read from the 
         * underlying {@code InputStream}.
         */
        private Chunk endChunk = new Chunk(0);
        /**
         * Generates ids for branches 
         */
        private AtomicLong idGenerator = new AtomicLong(0);
        
        /**
         * The class {@code Branch} is a concrete implementation of the abstract
         * {@code BranchInputStream}.
         */
        private class Branch extends BranchInputStream {
            /**
             * The current offset relative to the issue of the 
             * underlying {@code InputStream}.
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
            public BranchInputStream[] branch(final int count) throws IOException {
                synchronized(Root.this) {
                    BranchInputStream[] res;

                    if (isClosed()) {
                        throw new IOException("Cannot branch closed stream");
                    }
                    res = new BranchInputStream[count];
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
            public void close() {
                synchronized (Root.this) {
                    isClosed.set(true);
                    branches.remove(id);
                    if (branches.isEmpty() && source != null) {
                        source = null;
                    }
                }
            }

            @Override
            public int read() throws IOException {
                int res  = -1;
                
                if(!isClosed.get()) {
                    boolean canRead = true;
                    if (position + 1 > endChunk.offset + endChunk.length) {
                        synchronized(Root.this) {
                            long dataLength = endChunk.offset + endChunk.length;
                            if (position + 1 > dataLength && !isSourceEnded) {
                                /*
                                 * Should read from the underlying
                                 * {@code InputSource}.
                                 */
                                int leftReadCount = (int) (chunkSize);
                                /*
                                 * Allocate new chunk and add it to list
                                 */
                                endChunk.next = new Chunk(chunkSize);
                                endChunk.next.offset = dataLength;
                                endChunk = endChunk.next;

                                while (leftReadCount > 0) {
                                    int n = source.read(endChunk.buffer, 0, 
                                            leftReadCount);
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
                    
                    if (position >= chunk.offset + chunk.length) {
                        canRead = false;
                        if(chunk.next != null) {
                            canRead = true;
                            chunk = chunk.next;
                        }
                    }
                    if(canRead) {
                        res = chunk.buffer[(int)(position - chunk.offset)] & 0xFF;
                        position++;
                    }
                }
                return res;
            }

            @Override
            public BranchInputStream[] getBranches() {
                synchronized (Root.this) {
                    if(branches.isEmpty()) {
                        return new BranchInputStream[]{};
                    }
                    return branches.values().stream().toArray(BranchInputStream[]::new);
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
                        branch.close();
                    }
                }
                return true;
            }

        }
        
        /**
         * Creates new {@code Root} object with an underlying {@code InputStream}.
         * 
         * @param source the underlying {@code InputStream}.
         * @param chunkSize defines size of byte chunk instead of default one.
         */
        private Root(final InputStream source, final int chunkSize) {
            this.chunkSize = chunkSize;
            this.source = source;
        }

        /**
         * Creates new {@code Root} object with an underlying {@code InputStream}.
         * 
         * @param source the underlying {@code InputStream}.
         */
        private Root(final InputStream source) {
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
