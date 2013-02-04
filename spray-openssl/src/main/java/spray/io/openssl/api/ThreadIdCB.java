package spray.io.openssl.api;

import org.bridj.Callback;

public abstract class ThreadIdCB extends Callback<ThreadIdCB > {
    public abstract void apply(long ptr);
}
