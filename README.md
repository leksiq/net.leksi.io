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
    
## Docs

[javadoc](http://leksi.net/branch-input/javadoc/)

[coverage report](http://leksi.net/branch-input/report/)

