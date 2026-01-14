package com.epam.aidial.deployment.manager.docker;

import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class HttpConnectionFactory {

    public HttpURLConnection createConnection(URL url) throws Exception {
        return (HttpURLConnection) url.openConnection();
    }
}
