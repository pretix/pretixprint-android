package io.github.benoitduffez.cupsprint;

import android.content.Context;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.benoitduffez.cupsprint.ssl.AdditionalKeyManager;
import io.github.benoitduffez.cupsprint.ssl.AdditionalKeyStoresSSLSocketFactory;
import io.github.benoitduffez.cupsprint.ssl.AndroidCupsHostnameVerifier;

public class HttpConnectionManagement {
    private static final String KEYSTORE_FILE = "cupsprint-trustfile";

    private static final String KEYSTORE_PASSWORD = "i6:[(mW*xh~=Ni;S|?8lz8eZ;!SU(S";

    /**
     * Will handle SSL related stuff to this connection so that certs are properly managed
     *
     * @param connection The target https connection
     */
    public static void handleHttpsUrlConnection(@NonNull HttpsURLConnection connection) {
        connection.setHostnameVerifier(new AndroidCupsHostnameVerifier());

        try {
            KeyStore trustStore = loadKeyStore();
            if (trustStore == null) {
                return;
            }

            KeyManager keyManager = null;
            try {
                keyManager = AdditionalKeyManager.fromAlias();
            } catch (CertificateException e) {
                L.e("Couldn't load system key store: " + e.getLocalizedMessage(), e);
            }

            connection.setSSLSocketFactory(new AdditionalKeyStoresSSLSocketFactory(keyManager, trustStore));
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException | KeyManagementException e) {
            L.e("Couldn't handle SSL URL connection: " + e.getLocalizedMessage(), e);
        }
    }

    /**
     * Try to get the contents of the local key store
     *
     * @return A valid KeyStore object if nothing went wrong, null otherwise
     */
    @Nullable
    private static KeyStore loadKeyStore() {
        KeyStore trustStore;
        try {
            trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (KeyStoreException e) {
            L.e("Couldn't open local key store", e);
            return null;
        }

        // Load the local keystore into memory
        try {
            FileInputStream fis = CupsPrintApp.getContext().openFileInput(KEYSTORE_FILE);
            trustStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
            return trustStore;
        } catch (FileNotFoundException e) {
            // This one can be ignored safely - at least not sent to crashlytics
            L.e("Couldn't open local key store: " + e.getLocalizedMessage());
        } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
            L.e("Couldn't open local key store", e);
        }

        // if we couldn't load local keystore file, create an new empty one
        try {
            trustStore.load(null, null);
        } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
            L.e("Couldn't create new key store", e);
        }

        return trustStore;
    }

    /**
     * Add certs to the keystore (thus trusting them)
     *
     * @param chain The chain of certs to trust
     * @return true if it was saved, false otherwise
     */
    public static boolean saveCertificates(X509Certificate[] chain) {
        // Load existing certs
        KeyStore trustStore = loadKeyStore();
        if (trustStore == null) {
            return false;
        }

        // Add new certs
        try {
            for (final X509Certificate c : chain) {
                trustStore.setCertificateEntry(c.getSubjectDN().toString(), c);
            }
        } catch (final KeyStoreException e) {
            L.e("Couldn't store cert chain into key store", e);
            return false;
        }

        // Save new keystore
        FileOutputStream fos = null;
        try {
            fos = CupsPrintApp.getContext().openFileOutput(KEYSTORE_FILE, Context.MODE_PRIVATE);
            trustStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
            fos.close();
        } catch (final Exception e) {
            L.e("Unable to save key store", e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    L.e("Couldn't close key store", e);
                }
            }
        }

        return true;
    }

    /**
     * See if there are some basic auth credentials saved, and configure the connection
     *
     * @param url        URL we're about to request
     * @param connection The connection to be configured
     */
    public static void handleBasicAuth(URL url, HttpURLConnection connection) {
        // TODO: Unsupported
        L.e("Couldn't handle basic auth");
    }
}
