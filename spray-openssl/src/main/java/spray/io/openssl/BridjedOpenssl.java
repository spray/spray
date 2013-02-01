package spray.io.openssl;

import org.bridj.*;
import org.bridj.ann.Library;
import org.bridj.ann.Ptr;

@Library("ssl")
public class BridjedOpenssl {
    static {
        BridJ.register();
    }

    public static native long BIO_new(long method);
    public static native int BIO_new_bio_pair(
        Pointer<Long> bio1,
        @Ptr long writebuf1,
        Pointer<Long> bio2,
        @Ptr long writebuf2);

    public static native int BIO_write(@Ptr long bio, long wbuf, int wlen);
    public static native int BIO_read(@Ptr long bio, long wbuf, int wlen);
    public static native int BIO_set_flags(@Ptr long bio, int flags);
    public static native int BIO_ctrl_pending(@Ptr long bio);

    public static native int SSL_library_init();

    public static native long SSLv23_method();
    public static native long SSLv23_client_method();

    public static native long SSL_CTX_new(@Ptr long method);
    public static native int SSL_CTX_set_default_verify_paths(@Ptr long ctx);
    public static native void SSL_CTX_set_verify(@Ptr long ctx, int mode, long callback);
                                //int (*verify_callback)(int, X509_STORE_CTX *));
    public static native int SSL_CTX_set_cipher_list(@Ptr long ctx, long pStr);
    public static native int SSL_CTX_use_PrivateKey_file(@Ptr long ctx, Pointer<Byte> fileName, int type);
    public static native int SSL_CTX_use_certificate_chain_file(@Ptr long ctx, Pointer<Byte> fileName);
    public static native X509_STORE SSL_CTX_get_cert_store(@Ptr long ctx);

    public static native long SSL_CTX_ctrl(@Ptr long ctx, int cmd, long larg, long parg);


    public static native long SSL_new(@Ptr long ctx);

    public static native void SSL_set_bio(
        @Ptr long ssl,
        long rbio,
        long wbio);

    public static native int SSL_connect(@Ptr long ssl);
    public static native int SSL_accept(@Ptr long ssl);
    public static native void SSL_set_accept_state(@Ptr long ssl);

    public static native int SSL_write(@Ptr long ssl, long wbuf, int wlen);
    public static native int SSL_read(@Ptr long ssl, long wbuf, int wlen);

    public static native int SSL_want(@Ptr long ssl);
    public static native int SSL_pending(@Ptr long ssl);

    public static native int SSL_get_error(@Ptr long ssl, int ret);
    public static native int SSL_set_info_callback(@Ptr long ssl, Pointer<InfoCallback> callback);

    public static abstract class InfoCallback extends Callback<InfoCallback> {
        abstract public void apply(/*Pointer<SSL>*/ long ssl, int where, int ret);
    }

    public static native void SSL_load_error_strings();
    public static native long ERR_get_error();
    public static native void ERR_error_string_n(long err, Pointer<Byte> buffer, int len);

    public static final int BIO_CTRL_FLUSH = 11;

    public native static int CRYPTO_num_locks();
	public native static void CRYPTO_set_locking_callback(Pointer<LockingCB> arg);
	public native static int CRYPTO_THREADID_set_callback(Pointer<ThreadIdCB> arg);
	public native static void CRYPTO_THREADID_set_numeric(long ptr, long val);

    public static native X509Certificate d2i_X509_bio(BIO bio, @Ptr long resPtr);

    public static native long d2i_PKCS8_PRIV_KEY_INFO_bio(BIO bio, long retPtr);
    public static native EVP_PKEY EVP_PKCS82PKEY(@Ptr long pkcs8Key);

    public static native int X509_STORE_add_cert(X509_STORE store, X509Certificate x509);
}
