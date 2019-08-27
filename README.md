# BranchReader

The class BranchReader is designed for parallel or sequential reading one data source by several consumers. It may be convenient while parsing, for example. One could use one branch per choice item.

## Examples
  import net.leksi.io.BranchReader;
 
  ...
 
  try(
      FileReader fileReader = new FileReader(path);
      BranchReader branchReader = new BranchReader(fileReader);
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


[javadoc](http://leksi.net/branch-reader/javadoc/)

[coverage report](http://leksi.net/branch-reader/report/)

