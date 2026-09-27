package net.lintim.exception;

public class InputEncodingException extends LinTimException {
    public InputEncodingException(String fileName) {
        super("Error I2: File " + fileName + " has wrong encoding.");
    }
}
