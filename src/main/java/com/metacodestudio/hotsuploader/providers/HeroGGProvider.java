package com.metacodestudio.hotsuploader.providers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metacodestudio.hotsuploader.models.ReplayFile;
import com.metacodestudio.hotsuploader.models.Status;
import com.metacodestudio.hotsuploader.utils.IOUtils;
import org.apache.commons.codec.binary.Base64;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

public class HeroGGProvider extends Provider {

    private static final String ACCESS_KEY_ID = "beta:anQA9aBp";
    private static final String ENCODING = "UTF-8";
    private static final ObjectMapper mapper = new ObjectMapper();
    public static final String URI = "http://upload.hero.gg/ajax/upload-replay";

    public HeroGGProvider() {
        super("Hero.GG");
    }

    @Override
    public Status upload(final ReplayFile replayFile) {
        final String boundary = String.format("----------%s", UUID.randomUUID().toString().replaceAll("-", ""));
        final String contentType = "multipart/form-data; boundary=" + boundary;

        try {
            final URL url = new URL(URI);

            final Boolean status = sendRequest(replayFile, boundary, contentType, url);

            if (status != null && status) {
                return Status.UPLOADED;
            } else {
                return Status.EXCEPTION;
            }

        } catch (UnsupportedEncodingException | ProtocolException | MalformedURLException | JsonParseException | JsonMappingException e) {
            return Status.EXCEPTION;
        } catch (IOException e) {
            return Status.NEW;
        }

    }

    @SuppressWarnings("unchecked")
    private Boolean sendRequest(final ReplayFile replayFile, final String boundary, final String contentType, final URL url) throws IOException {
        HttpURLConnection connection = null;
        try {
            final byte[] fileData = getFileData(replayFile, boundary);
            connection = setupHttpURLConnection(contentType, url, fileData);

            try (OutputStream requestStream = connection.getOutputStream()) {
                requestStream.write(fileData, 0, fileData.length);
            }

            final String result = IOUtils.readInputStream(connection.getInputStream());

            Map<String, Object> resultMap = mapper.readValue(result, Map.class);
            return (Boolean) resultMap.get("success");
        } finally {
            if(connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection setupHttpURLConnection(final String contentType, final URL url, final byte[] fileData) throws IOException {
        final HttpURLConnection connection;
        connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("User-Agent", "HeroGG");
        connection.setFixedLengthStreamingMode((long) fileData.length);
        connection.setRequestProperty("Authorization", "Basic " + new String(Base64.encodeBase64(ACCESS_KEY_ID.getBytes(ENCODING))));
        return connection;
    }

    private byte[] getFileData(final ReplayFile replayFile, String boundary) throws IOException {

        String key = getContentString(boundary, "key", "Nothing goes here at the moment");
        String name = getContentString(boundary, "name", replayFile.getFile().getName());
        String file = getContentString(boundary, "file", replayFile.getFile());
        String closing = "\r\n--" + boundary + "--\r\n";
        String newLine = "\r\n";

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            byte[] fileContents = Files.readAllBytes(replayFile.getFile().toPath());

            stream.write(key.getBytes(ENCODING));
            stream.write(newLine.getBytes(ENCODING));
            stream.write(name.getBytes(ENCODING));
            stream.write(newLine.getBytes(ENCODING));
            stream.write(file.getBytes(ENCODING));
            stream.write(fileContents);
            stream.write(closing.getBytes(ENCODING));

            return stream.toByteArray();
        }
    }

    private String getContentString(String boundary, String key, String value) {
        Object[] params = new Object[]{boundary, key, value};

        return String.format("--%1$s\r\nContent-Disposition: form-data; name=\"%2$s\"\r\n\r\n%3$s", params);
    }

    private String getContentString(String boundary, String key, File value) {
        Object[] params = new Object[]{boundary, key, value.getName(), "application/x-www-form-urlencoded"};

        return String.format("--%1$s\r\nContent-Disposition: form-data; name=\"%2$s\"; filename=\"%3$s\"\r\nContent-Type: %4$s\r\n\r\n", params);
    }
}
