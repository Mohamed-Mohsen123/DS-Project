import java.io.*;
import java.net.*;
import java.nio.file.*;

public class FileServer {

    // ─────────────────────────────────────────────────────────────
    // Default configuration constants.
    // DEFAULT_PORT  — the TCP port the server listens on.
    // SAVE_DIRECTORY — folder where received files are stored.
    //                  Relative to wherever the server is launched from.
    // Override port at runtime: java FileServer <port>
    // ─────────────────────────────────────────────────────────────
    private static final int DEFAULT_PORT = 5000;
    private static final String SAVE_DIRECTORY = "received_files";

    public static void main(String[] args) {

        // ─────────────────────────────────────────────────────────
        // Step 1: Parse optional port argument.
        // If the user runs: java FileServer 8080
        // then port = 8080. Otherwise, use 5000.
        // ─────────────────────────────────────────────────────────
        int port = DEFAULT_PORT;

        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }

        // ─────────────────────────────────────────────────────────
        // Step 2: Create the save directory if it doesn't exist.
        // createDirectories() is safe to call even if the folder
        // already exists — it simply does nothing in that case.
        // If it fails (e.g. no write permission), we print an error
        // and exit immediately, since there is nowhere to save files.
        // ─────────────────────────────────────────────────────────
        try {
            Files.createDirectories(Paths.get(SAVE_DIRECTORY));
        } catch (IOException e) {
            System.err.println("Could not create save directory: " + e.getMessage());
            return;
        }

        // ─────────────────────────────────────────────────────────
        // Step 3: Open the server socket and start listening.
        //
        // ServerSocket binds to the given port and queues up
        // incoming TCP connection requests from clients.
        //
        // try-with-resources ensures the ServerSocket is closed
        // cleanly if the server ever exits.
        // ─────────────────────────────────────────────────────────
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("File server started.");
            System.out.println("Listening on port: " + port);
            System.out.println("Saving files to: " + Paths.get(SAVE_DIRECTORY).toAbsolutePath());
            System.out.println();

            // ─────────────────────────────────────────────────────
            // Step 4: Main accept loop.
            // The server runs forever, handling one client at a time.
            // When a client disconnects, the loop immediately waits
            // for the next one. To stop the server, press Ctrl+C.
            // ─────────────────────────────────────────────────────
            while (true) {
                System.out.println("Waiting for client...");

                // ─────────────────────────────────────────────────
                // Step 5: Block until a client connects.
                // serverSocket.accept() pauses here until a client
                // calls new Socket(host, port). Once connected, it
                // returns a Socket representing that specific client.
                //
                // The Socket is opened inside a try-with-resources
                // so it is closed automatically after handleClient()
                // returns, which also closes the network connection
                // to that client.
                // ─────────────────────────────────────────────────
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Client connected: " + clientSocket.getInetAddress());

                    // ─────────────────────────────────────────────
                    // Step 6: Handle this client's file transfers.
                    // All the file-receiving logic lives in
                    // handleClient(). We pass the socket so it can
                    // read the incoming data stream.
                    // ─────────────────────────────────────────────
                    handleClient(clientSocket);

                } catch (IOException e) {
                    // ─────────────────────────────────────────────
                    // A problem occurred with this specific client
                    // (e.g. they disconnected abruptly). Log it and
                    // keep the server running for the next client —
                    // do NOT let one bad client crash the server.
                    // ─────────────────────────────────────────────
                    System.err.println("Client connection error: " + e.getMessage());
                }

                System.out.println("Client disconnected.");
                System.out.println();
            }

        } catch (IOException e) {
            // ─────────────────────────────────────────────────────
            // Fatal server error — e.g. the port is already in use
            // by another program. Nothing we can do; print and exit.
            // ─────────────────────────────────────────────────────
            System.err.println("Server error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // handleClient()
    //
    // Reads and saves all files sent by a single connected client.
    // Implements the server side of our 3-part protocol:
    //
    //   Loop per file:
    //     1. readUTF()  → receive filename (or "__END__" to stop)
    //     2. readLong() → receive file size in bytes
    //     3. read()     → receive raw bytes until size is reached
    //
    // Parameters:
    //   clientSocket — the TCP socket for the connected client
    // ─────────────────────────────────────────────────────────────────
    private static void handleClient(Socket clientSocket) throws IOException {

        // ─────────────────────────────────────────────────────────
        // Wrap the socket's input stream in:
        // - BufferedInputStream: batches small reads for efficiency
        // - DataInputStream: lets us call typed read methods like
        //   readUTF() and readLong() that mirror what the client
        //   wrote with writeUTF() and writeLong()
        //
        // try-with-resources closes the stream when we are done.
        // ─────────────────────────────────────────────────────────
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(clientSocket.getInputStream()))) {

            // ─────────────────────────────────────────────────────
            // Step 7: File-receiving loop.
            // We don't know how many files the client will send, so
            // we loop until we see the "__END__" sentinel.
            // ─────────────────────────────────────────────────────
            while (true) {

                // ─────────────────────────────────────────────────
                // Protocol Part 1: read the filename.
                // readUTF() reads the 2-byte length prefix that
                // writeUTF() wrote, then reads exactly that many
                // bytes and decodes them as a UTF-8 string.
                // ─────────────────────────────────────────────────
                String fileName = in.readUTF();

                // ─────────────────────────────────────────────────
                // Check for the termination sentinel.
                // The client sends "__END__" instead of a filename
                // when the user is done. When we see it, break out
                // of the loop and let the connection close.
                // ─────────────────────────────────────────────────
                if ("__END__".equals(fileName)) {
                    System.out.println("Client finished sending files.");
                    break;
                }

                // ─────────────────────────────────────────────────
                // Protocol Part 2: read the file size.
                // readLong() reads exactly 8 bytes and converts
                // them to a 64-bit integer. We use this as a
                // countdown to know when we have received all bytes
                // for this file.
                // ─────────────────────────────────────────────────
                long fileSize = in.readLong();

                // ─────────────────────────────────────────────────
                // Security: prevent path traversal attacks.
                // A malicious client could send a filename like
                // "../../etc/passwd" to try to overwrite system
                // files. getFileName() strips all directory
                // components and returns only the bare filename,
                // e.g. "passwd". The file is then always saved
                // inside SAVE_DIRECTORY, never outside it.
                // ─────────────────────────────────────────────────
                Path safeFileName = Paths.get(fileName).getFileName();

                // ─────────────────────────────────────────────────
                // Compute the final output path.
                // getUniquePath() handles the case where a file with
                // this name already exists, automatically appending
                // _1, _2, etc. to avoid overwriting existing files.
                // ─────────────────────────────────────────────────
                Path outputPath = getUniquePath(Paths.get(SAVE_DIRECTORY).resolve(safeFileName));

                System.out.println("Receiving file: " + safeFileName);
                System.out.println("Size: " + fileSize + " bytes");

                // ─────────────────────────────────────────────────
                // Protocol Part 3: receive and write the raw bytes.
                //
                // - CREATE_NEW ensures the file is created fresh and
                //   fails atomically if a duplicate appears between
                //   our check and the write (race-condition safety).
                // - BufferedOutputStream batches the writes to disk
                //   for better performance.
                // - We read in 8 KB chunks (matching the client's
                //   send buffer size).
                // - Math.min() prevents reading more bytes than what
                //   the server declared — important for the last
                //   chunk, which may be smaller than 8192 bytes.
                // - If read() returns -1 the connection dropped
                //   mid-transfer; we throw EOFException so the
                //   partial file isn't silently accepted as complete.
                // - "remaining" counts down from fileSize to 0.
                //   When it hits 0, we have the full file.
                // ─────────────────────────────────────────────────
                try (OutputStream fileOut = new BufferedOutputStream(
                        Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW))) {

                    byte[] buffer = new byte[8192];
                    long remaining = fileSize;

                    while (remaining > 0) {
                        // Read up to 8 KB, but never more than
                        // the bytes still expected for this file.
                        int bytesToRead = (int) Math.min(buffer.length, remaining);
                        int bytesRead = in.read(buffer, 0, bytesToRead);

                        if (bytesRead == -1) {
                            // Client disconnected before sending
                            // the full file — abort this transfer.
                            throw new EOFException("Connection lost while receiving file.");
                        }

                        fileOut.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }

                System.out.println("Saved as: " + outputPath.toAbsolutePath());
                System.out.println();

                // Loop back to wait for the next filename (or "__END__").
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // getUniquePath()
    //
    // Returns a path that does not already exist on disk.
    // If the original path is free, it is returned as-is.
    // If it is taken, the method appends a counter:
    //   report.pdf → report_1.pdf → report_2.pdf → ...
    //
    // Parameters:
    //   originalPath — the desired save path
    // Returns:
    //   a Path guaranteed not to exist at the time of this call
    // ─────────────────────────────────────────────────────────────────
    private static Path getUniquePath(Path originalPath) {

        // ─────────────────────────────────────────────────────────
        // Happy path: file doesn't exist yet, use it directly.
        // ─────────────────────────────────────────────────────────
        if (!Files.exists(originalPath)) {
            return originalPath;
        }

        // ─────────────────────────────────────────────────────────
        // Split the filename into base name and extension so we
        // can insert the counter between them.
        // e.g. "report.pdf" → name="report", extension=".pdf"
        //      "archive"    → name="archive", extension=""
        //
        // dotIndex > 0 (not >= 0) intentionally ignores hidden
        // Unix files like ".bashrc" whose dot is the first character
        // — those are treated as having no extension.
        // ─────────────────────────────────────────────────────────
        String fileName = originalPath.getFileName().toString();
        String name;
        String extension;

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            name = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        } else {
            name = fileName;
            extension = "";
        }

        Path parent = originalPath.getParent();
        int counter = 1;

        // ─────────────────────────────────────────────────────────
        // Try report_1.pdf, report_2.pdf, ... until we find a name
        // that is not yet taken. In practice, counter rarely exceeds
        // 1 or 2 unless many identical files are sent in a session.
        // ─────────────────────────────────────────────────────────
        while (true) {
            Path candidate = parent.resolve(name + "_" + counter + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            counter++;
        }
    }
}