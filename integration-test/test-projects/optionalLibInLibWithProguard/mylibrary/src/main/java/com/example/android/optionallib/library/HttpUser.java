package com.example.android.optionallib.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class HttpUser {

    @SuppressWarnings("deprecation")
    public static String downloadStuff() {
        try {
            DefaultHttpClient client = new DefaultHttpClient();
            HttpGet get = new HttpGet("http://www.google.com");

            HttpResponse response = client.execute(get);
            HttpEntity entity = response.getEntity();

            BufferedReader rd = new BufferedReader(new InputStreamReader(entity.getContent()));
            StringBuffer buffer = new StringBuffer();
            String line;
            while ((line = rd.readLine()) != null) {
                buffer.append(line);
            }

            return buffer.toString();

        } catch (IOException e) {
            return e.getMessage();
        }
    }

    public static long getHttpEntityContentLength(HttpEntity httpEntity) {
        return httpEntity.getContentLength();
    }
}
