package com.analyzer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class MultipartParser {

    public static String extractFileContent(InputStream body, String contentType) throws Exception {
        String boundary = null;
        for (String part : contentType.split(";")) {
            String p = part.trim();
            if (p.startsWith("boundary=")) { boundary = p.substring(9).trim(); break; }
        }
        if (boundary == null) throw new Exception("No boundary in Content-Type");

        String bodyStr = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        String delimiter = "--" + boundary;
        String[] parts = bodyStr.split(Pattern.quote(delimiter));

        for (String part : parts) {
            if (part.contains("Content-Disposition") && part.contains("filename=")) {
                int headerEnd = part.indexOf("\r\n\r\n");
                int offset = 4;
                if (headerEnd == -1) { headerEnd = part.indexOf("\n\n"); offset = 2; }
                if (headerEnd == -1) continue;
                String content = part.substring(headerEnd + offset);
                if (content.endsWith("\r\n")) content = content.substring(0, content.length() - 2);
                if (content.endsWith("\n"))   content = content.substring(0, content.length() - 1);
                return content;
            }
        }
        throw new Exception("No file found in form");
    }
}
