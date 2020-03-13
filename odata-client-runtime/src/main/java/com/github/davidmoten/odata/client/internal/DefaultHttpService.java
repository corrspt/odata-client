package com.github.davidmoten.odata.client.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.github.davidmoten.guavamini.Lists;
import com.github.davidmoten.odata.client.ClientException;
import com.github.davidmoten.odata.client.HttpMethod;
import com.github.davidmoten.odata.client.HttpResponse;
import com.github.davidmoten.odata.client.HttpService;
import com.github.davidmoten.odata.client.Path;
import com.github.davidmoten.odata.client.RequestHeader;

public final class DefaultHttpService implements HttpService {

    private final Path basePath;
    private final Function<List<RequestHeader>, List<RequestHeader>> requestHeadersModifier;

    // not thread-safe but is ok if set multiple times from multiple threads (just
    // means a few extra calls that throw till all threads catch up to the setting
    private boolean patchSupported = true;
    private final Consumer<HttpURLConnection> consumer;

    public DefaultHttpService(Path basePath,
            Function<List<RequestHeader>, List<RequestHeader>> requestHeadersModifier,
            Consumer<HttpURLConnection> consumer) {
        this.basePath = basePath;
        this.requestHeadersModifier = requestHeadersModifier;
        this.consumer = consumer;
    }

    @Override
    public HttpResponse get(String url, List<RequestHeader> requestHeaders) {
        return getResponse(url, requestHeaders, HttpMethod.GET, true, null);
    }

    @Override
    public HttpResponse patch(String url, List<RequestHeader> requestHeaders, InputStream content) {
        if (patchSupported) {
            try {
                return getResponse(url, requestHeaders, HttpMethod.PATCH, false, content);
            } catch (ProtocolRuntimeException e) {
                return getResponsePatchOverride(url, requestHeaders, content);
            }
        } else {
            return getResponsePatchOverride(url, requestHeaders, content);
        }
    }

    private HttpResponse getResponsePatchOverride(String url, List<RequestHeader> requestHeaders,
            InputStream content) {
        List<RequestHeader> list = Lists.newArrayList(requestHeaders);
        list.add(new RequestHeader("X-HTTP-Method-Override", "PATCH"));
        HttpResponse result = getResponse(url, list, HttpMethod.POST, false, content);
        // only indicate patch not supported if the result is returned ok
        patchSupported = false;
        return result;
    }

    @Override
    public HttpResponse put(String url, List<RequestHeader> requestHeaders, InputStream content) {
        return getResponse(url, requestHeaders, HttpMethod.PUT, false, content);
    }

    @Override
    public HttpResponse post(String url, List<RequestHeader> requestHeaders, InputStream content) {
        return getResponse(url, requestHeaders, HttpMethod.POST, true, content);
    }

    @Override
    public HttpResponse delete(String url, List<RequestHeader> requestHeaders) {
        return getResponse(url, requestHeaders, HttpMethod.DELETE, false, null);
    }

    @Override
    public Path getBasePath() {
        return basePath;
    }

    private HttpResponse getResponse(String url, List<RequestHeader> requestHeaders,
            HttpMethod method, boolean doInput, InputStream content) {
        try {
            URL u = new URL(url);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod(method.toString());
            for (RequestHeader header : requestHeadersModifier.apply(requestHeaders)) {
                c.setRequestProperty(header.name(), header.value());
            }
            c.setDoInput(doInput);
            c.setDoOutput(content != null);
            // apply just before connection established so further configuration can take
            // place
            // like timeouts
            consumer.accept(c);
            if (content != null) {
                try (OutputStream out = c.getOutputStream()) {
                    byte[] b = new byte[8192];
                    int len;
                    while ((len = content.read(b))!= -1) {
                        out.write(b, 0, len);
                    }
                }
            }
            final String text;
            if (doInput) {
                text = Util.readString(c.getInputStream(), StandardCharsets.UTF_8);
            } else {
                text = null;
            }
            return new HttpResponse(c.getResponseCode(), text);
        } catch (ProtocolException e) {
            throw new ProtocolRuntimeException(e);
        } catch (IOException e) {
            throw new ClientException(e);
        }
    }

    @Override
    public void close() throws Exception {
        // do nothing
    }

    @Override
    public InputStream getStream(String url, List<RequestHeader> requestHeaders) {
        try {
            URL u = new URL(url);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod(HttpMethod.GET.toString());
            for (RequestHeader header : requestHeadersModifier.apply(requestHeaders)) {
                c.setRequestProperty(header.name(), header.value());
            }
            c.setDoInput(true);
            c.setDoOutput(false);
            // apply just before connection established so further configuration can take
            // place like timeouts
            consumer.accept(c);
            // TODO check error code and throw message read from input stream

            return c.getInputStream();
        } catch (IOException e) {
            throw new ClientException(e);
        }

    }
}
