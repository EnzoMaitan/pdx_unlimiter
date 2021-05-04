package com.crschnick.pdx_unlimiter.core.parser;

import com.crschnick.pdx_unlimiter.core.node.ArrayNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public abstract class FormatParser {

    public static boolean validateHeader(byte[] header, InputStream stream) throws IOException {
        byte[] first = new byte[header.length];
        stream.readNBytes(first, 0, header.length);
        return Arrays.equals(first, header);
    }

    public static boolean validateHeader(byte[] header, byte[] content) {
        byte[] first = new byte[header.length];
        System.arraycopy(content, 0, first, 0, header.length);
        return Arrays.equals(first, header);
    }

    public final ArrayNode parse(Path in) throws Exception {
        return parse(Files.readAllBytes(in));
    }

    public final ArrayNode parse(byte[] input) throws Exception {
        return parse(input, 0);
    }

    public abstract ArrayNode parse(byte[] input, int start) throws Exception;
}
