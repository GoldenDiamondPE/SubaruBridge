package org.mlpi.pixelprobe.starlink;

/** Renewable, bounded active-session power lease; close wins over a racing timer. */
final class SessionPower implements AutoCloseable {
    interface Lock { void acquire(long timeoutMs); void release(); boolean held(); }
    static final long LEASE_MS=20*60*1000L,RENEW_MS=5*60*1000L;
    private final Lock lock;private long renewed;private boolean closed;
    SessionPower(Lock lock,long now){this.lock=lock;renewed=now;lock.acquire(LEASE_MS);}
    synchronized void refresh(long now){if(!closed&&now-renewed>=RENEW_MS){lock.acquire(LEASE_MS);renewed=now;}}
    @Override public synchronized void close(){if(!closed){closed=true;if(lock.held())lock.release();}}
}
