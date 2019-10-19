# BranchReader/BranchInputStream

The classes BranchReader and BranchInputStream are designed for independent parallel or sequential reading one data source by several consumers. It may be convenient while parsing, for example. One could use one branch per choice item.

## Examples

### Sequential
    
    import net.leksi.io.BranchReader;
 
    ...
 
    try(
        FileReader fileReader = new FileReader(path);
        BranchReader branchReader = BranchReader.create(fileReader);
    ) {
        BranchReader curReader = branchReader;
     
        ...
     
        for(Sequence seq: Choice.sequences) {
            BranchReader testReader = curReader.branch(1)[0];
            if(seq.test(testReader)) {
                curReader.close();
                curReader = testReader;
                break;
            }
        }
    }
    
### Parallel

    import net.leksi.io.BranchReader;
 
    ...
 
    try(
        FileReader fileReader = new FileReader(path);
        BranchReader branchReader = BranchReader.create(fileReader);
    ) {
        BranchReader curReader = branchReader;
     
        ...
        
        BranchReader[] readers = curReader.branch(Choice.sequences.size());
        CountDownLatch cdl = new CountDownLatch(readers.length + 1);
        CyclicBarrier barrier = new CyclicBarrier(readers.length, () -> cdl.countDown());
        IntStream.range(0, readers.length).forEach(i -> {
            final int pos = i;
            new Thread(() -> {
                try {
                    if(!Choice.sequences.get(pos).test(readers[pos])) {
                        readers[pos].close();
                    }
                    barrier.await();
                    cdl.countDown();
                } catch(Exception ex) {
                ...
                }
            }).start();
        });
        cdl.await();
        curReader.close();
        curReader = Arrays.stream(readers).filter(r -> !r.isClosed()).findFirst().orElse(null);
        ...
    }
    
# UTF7InputStream

A stream object of this class reads UTF-7 encoded data and gives it on UTF-16BE encoding. The BOM (Byte Order Mark) is skipped if present.

# BOM
Tests BOM (Byte Order Mark) of data from *InputStream* and 
returns the charset name. The *InputStream* after that has BOM 
skipped (except the case UTF-7, when the BOM should be decoded first)

## Examples
    ...
    try(FileInputStream stream = new FileInputStream.create(path);) {
        String charsetName = new BOM().test(stream);
        try(InputStreamReader isr = charsetName != null ? 
                ("UTF-7".equals(charsetName) ? 
                    new InputStreamReader(new UTF7InputStream(stream), "UTF-16BE") : 
                    new InputStreamReader(stream, charsetName)
                    ) :
                new InputStreamReader(stream);
                ) {
        ...
        }
    }
    
# Docs

[javadoc](http://leksi.net/net.leksi.io/javadoc/)

[coverage report](http://leksi.net/net.leksi.io/jacoco/)

# JAR

[net.leksi.io.jar](http://leksi.net/net.leksi.io/dist/net.leksi.io.jar)

[net.leksi.io.jar MD5](http://leksi.net/net.leksi.io/dist/net.leksi.io.jar.MD5)


