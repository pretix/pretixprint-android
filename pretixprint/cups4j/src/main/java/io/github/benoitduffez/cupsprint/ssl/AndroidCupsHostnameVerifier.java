package io.github.benoitduffez.cupsprint.ssl;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/**
 * Used with {@link HostNotVerifiedActivity} to trust certain hosts
 */
public class AndroidCupsHostnameVerifier implements HostnameVerifier {
    @Override
    public boolean verify(String hostname, SSLSession session) {
        // TODO: ask user?
        return true;
    }
}
