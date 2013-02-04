package spray.io.openssl.api;

import org.bridj.Callback;
import org.bridj.Pointer;

public abstract class LockingCB extends Callback<LockingCB > {
    public abstract void apply(int mode, int type, Pointer<Byte> file, int line);
}
