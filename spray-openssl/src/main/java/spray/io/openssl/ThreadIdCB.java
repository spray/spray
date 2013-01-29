package spray.io.openssl;

import org.bridj.Callback;

public abstract class ThreadIdCB extends Callback<ThreadIdCB > {
    public abstract void apply(long ptr);
}
