import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Scanner;

public class FileClient {

    // ─────────────────────────────────────────────────────────────
    // Default connection settings.
    // These are used when no command-line arguments are provided.
    // Override at runtime: java FileClient <host> <port>
    // ─────────────────────────────────────────────────────────────
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    public static void main(String[] args) {

        // ─────────────────────────────────────────────────────────
        // Step 1: Parse command-line arguments (optional).
        // args[0] = server hostname or IP address
        // args[1] = server port number
        // If not supplied, the DEFAULT values above are used.
        // ─────────────────────────────────────────────────────────
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length >= 1) {
            host = args[0];
        }

        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }

        // ─────────────────────────────────────────────────────────
        // Step 2: Open the TCP connection and set up I/O streams.
        //
        // - Socket: opens a TCP connection to the server. Throws
        //   ConnectException if the server is not running.
        //
        // - DataOutputStream: wraps the socket's output stream.
        //   Lets us send typed data (strings, longs, raw bytes)
        //   in a standard binary format that DataInputStream on
        //   the server side can read back correctly.
        //
        // - BufferedOutputStream: batches small writes into
        //   larger chunks before sending over the network,
        //   which is more efficient.
        //
        // - Scanner: reads file paths typed by the user in the
        //   terminal (System.in).
        //
        // All three are opened in a try-with-resources block so
        // they are automatically closed when we leave this block,
        // even if an exception occurs.
        // ─────────────────────────────────────────────────────────
        try (
                Socket socket = new Socket(host, port);
                DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(socket.getOutputStream()));
                Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Connected to server: " + host + ":" + port);
            System.out.println("Enter file paths to send.");
            System.out.println("Press Enter on an empty line when finished.");
            System.out.println();

            // ─────────────────────────────────────────────────────
            // Step 3: Main input loop.
            // Repeatedly asks the user to type a file path.
            // Keeps going until the user presses Enter on an
            // empty line (the termination signal).
            // ─────────────────────────────────────────────────────
            while (true) {
                System.out.print("File path: ");
                String input = scanner.nextLine().trim();

                // ─────────────────────────────────────────────────
                // Step 4: Handle empty input → send end signal.
                // If the user pressed Enter without typing anything,
                // send the special sentinel string "__END__" to tell
                // the server we are done sending files, then exit
                // the loop.
                // ─────────────────────────────────────────────────
                if (input.isEmpty()) {
                    out.writeUTF("__END__");
                    out.flush();
                    System.out.println("Done sending files.");
                    break;
                }

                // ─────────────────────────────────────────────────
                // Step 5: Validate the file before sending.
                // We turn the string the user typed into a Path
                // object, then perform two checks:
                //   1. Does the file actually exist on disk?
                //   2. Is it a regular file (not a directory or
                //      symbolic link pointing to nothing)?
                // If either check fails, we print a message and
                // loop back to ask for another path.
                // ─────────────────────────────────────────────────
                Path filePath = Paths.get(input);

                if (!Files.exists(filePath)) {
                    System.out.println("File does not exist.");
                    continue;
                }

                if (!Files.isRegularFile(filePath)) {
                    System.out.println("That path is not a regular file.");
                    continue;
                }

                // ─────────────────────────────────────────────────
                // Step 6: Send the file.
                // Validation passed — hand off to sendFile() which
                // handles all the actual network transfer logic.
                // ─────────────────────────────────────────────────
                sendFile(filePath, out);
            }

        } catch (ConnectException e) {
            // ─────────────────────────────────────────────────────
            // ConnectException means the TCP connection was refused —
            // the server is not running or is not reachable on the
            // given host:port. Give the user a helpful message
            // instead of a raw stack trace.
            // ─────────────────────────────────────────────────────
            System.err.println("Could not connect to server. Is FileServer running?");
        } catch (IOException e) {
            // ─────────────────────────────────────────────────────
            // Catch-all for any other I/O problems (network drop,
            // disk error, etc.).
            // ─────────────────────────────────────────────────────
            System.err.println("Client error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // sendFile()
    //
    // Sends a single file to the server using our custom 3-part protocol:
    //
    //   Part 1 — filename  (UTF-8 string, length-prefixed by writeUTF)
    //   Part 2 — file size (8-byte long, so the server knows how many
    //                        bytes to read)
    //   Part 3 — raw bytes (streamed in 8 KB chunks until EOF)
    //
    // Parameters:
    //   filePath — the validated path to the local file
    //   out      — the DataOutputStream connected to the server
    // ─────────────────────────────────────────────────────────────────
    private static void sendFile(Path filePath, DataOutputStream out) throws IOException {

        // ─────────────────────────────────────────────────────────
        // Extract just the filename (no parent directories).
        // e.g. "/home/user/docs/report.pdf" → "report.pdf"
        // This is what gets sent to the server as the file name.
        // ─────────────────────────────────────────────────────────
        String fileName = filePath.getFileName().toString();
        long fileSize = Files.size(filePath);

        System.out.println("Sending: " + fileName);
        System.out.println("Size: " + fileSize + " bytes");

        // ─────────────────────────────────────────────────────────
        // Protocol Part 1: send the filename.
        // writeUTF() writes a 2-byte length prefix followed by the
        // UTF-8 bytes of the string. The server calls readUTF()
        // which reads that length prefix first, so it knows exactly
        // how many bytes make up the filename.
        // ─────────────────────────────────────────────────────────
        out.writeUTF(fileName);

        // ─────────────────────────────────────────────────────────
        // Protocol Part 2: send the file size in bytes.
        // writeLong() sends exactly 8 bytes. The server stores this
        // as "remaining" and counts down to zero as it receives the
        // raw bytes — this is how it knows when the file is complete
        // without needing any special end-of-file marker in the data.
        // ─────────────────────────────────────────────────────────
        out.writeLong(fileSize);

        // ─────────────────────────────────────────────────────────
        // Protocol Part 3: stream the raw file bytes.
        //
        // - BufferedInputStream wraps the file read to reduce the
        //   number of disk I/O calls.
        // - We read up to 8 KB at a time into a buffer array.
        // - fileIn.read(buffer) returns the number of bytes actually
        //   read, which may be less than 8192 (e.g. at end of file).
        //   We use that count when writing to avoid sending garbage
        //   bytes from the end of the buffer.
        // - The loop ends when read() returns -1 (end of file).
        // - try-with-resources ensures the file input stream is
        //   closed automatically after the loop.
        // ─────────────────────────────────────────────────────────
        try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(filePath))) {
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        // ─────────────────────────────────────────────────────────
        // Flush the BufferedOutputStream.
        // Without this, some bytes could still be sitting in the
        // buffer and never reach the server. flush() forces all
        // buffered data to be written to the network socket.
        // ─────────────────────────────────────────────────────────
        out.flush();

        System.out.println("Sent successfully.");
        System.out.println();
    }
}