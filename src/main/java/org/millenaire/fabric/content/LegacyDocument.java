package org.millenaire.fabric.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Lossless ordered parameter input, including repeated quest steps and raw table rows. */
public record LegacyDocument(String source, String encoding, List<String> lines, Map<String, List<String>> fields) {
    public LegacyDocument {
        lines = List.copyOf(lines);
        Map<String, List<String>> copy = new LinkedHashMap<>();
        fields.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        fields = Collections.unmodifiableMap(copy);
    }

    public static LegacyDocument read(Path path, String source) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic link in content: " + path);
        byte[] bytes = Files.readAllBytes(path);
        String text, encoding = "UTF-8";
        if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) {
            encoding = "UTF-16LE";
            text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) {
            encoding = "UTF-16BE";
            text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        } else {
            try {
                text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException exception) {
                encoding = "windows-1252";
                text = new String(bytes, Charset.forName(encoding));
            }
        }
        List<String> lines = text.lines().toList();
        Map<String, List<String>> fields = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.replace("\uFEFF", "").trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            int equals = line.indexOf('=');
            int colon = line.indexOf(':');
            int separator = equals < 0 ? colon : colon < 0 ? equals : Math.min(equals, colon);
            if (separator <= 0) continue; // name lists and semicolon tables are retained in lines
            String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            fields.computeIfAbsent(key, ignored -> new ArrayList<>()).add(line.substring(separator + 1).trim());
        }
        return new LegacyDocument(source, encoding, lines, fields);
    }

    public List<String> values(String key) { return fields.getOrDefault(key.toLowerCase(Locale.ROOT), List.of()); }
    public String first(String key, String fallback) {
        List<String> values = values(key);
        return values.isEmpty() ? fallback : values.getFirst();
    }
}
