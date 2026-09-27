package io.github.ghosthack.turismo.multipart;

/**
 * A file uploaded in a {@code multipart/form-data} body.
 *
 * <p>{@code fileName} is exactly what the client sent and is untrusted: it
 * may be empty, contain path separators ({@code /} or {@code \}), or
 * {@code ..} segments. Never use it as a filesystem path without
 * sanitizing it first.</p>
 *
 * @param contentType the part's {@code Content-Type}, or
 *                    {@code application/octet-stream} when absent
 * @param fileName    the client-supplied file name (untrusted)
 * @param content     the file bytes; the array is not copied
 */
public record FilePart(String contentType, String fileName, byte[] content) {
}
