package org.mlpi.pixelprobe.starlink;

import java.io.*;
import java.nio.file.Files;

/** Keep the current and one previous journal, acknowledging only flushed writes. */
final class BoundedJournal {
    static final int LIMIT=1024*1024;
    static synchronized void append(File current,File previous,byte[] bytes) throws IOException {
        if(bytes.length>LIMIT)throw new IOException("Journal event exceeds capacity");
        if(Files.isSymbolicLink(current.toPath())||Files.isSymbolicLink(previous.toPath()))throw new IOException("Journal link rejected");
        if(current.length()+bytes.length>LIMIT) {
            if((previous.exists()&&!previous.delete())||!current.renameTo(previous))throw new IOException("Journal rotation failed");
        }
        try(FileOutputStream out=new FileOutputStream(current,true)){out.write(bytes);out.getFD().sync();}
    }
    private BoundedJournal(){}
}
