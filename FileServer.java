import java.io.*;
import java.net.*;
import java.nio.file.*;

public class FileServer {
    private static final int DEFAULT_PORT = 5000;
    private static final String SAVE_DIRECTORY = "received_files";

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }

        try {
            Files.createDirectories(Paths.get(SAVE_DIRECTORY));
        } catch (IOException e) {
            System.err.println("Could not create save directory: " + e.getMessage());
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("File server started.");
            System.out.println("Listening on port: " + port);
            System.out.println("Saving files to: " + Paths.get(SAVE_DIRECTORY).toAbsolutePath());
            System.out.println();

            while (true) {
                System.out.println("Waiting for client...");
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Client connected: " + clientSocket.getInetAddress());
                    handleClient(clientSocket);
                } catch (IOException e) {
                    System.err.println("Client connection error: " + e.getMessage());
                }

                System.out.println("Client disconnected.");
                System.out.println();
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(clientSocket.getInputStream()))) {

            while (true) {
                String fileName = in.readUTF();

                if ("__END__".equals(fileName)) {
                    System.out.println("Client finished sending files.");
                    break;
                }

                long fileSize = in.readLong();

                // Prevent path traversal. Only use the file name, not directories from client path.
                Path safeFileName = Paths.get(fileName).getFileName();
                Path outputPath = getUniquePath(Paths.get(SAVE_DIRECTORY).resolve(safeFileName));

                System.out.println("Receiving file: " + safeFileName);
                System.out.println("Size: " + fileSize + " bytes");

                try (OutputStream fileOut = new BufferedOutputStream(
                        Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW))) {

                    byte[] buffer = new byte[8192];
                    long remaining = fileSize;

                    while (remaining > 0) {
                        int bytesToRead = (int) Math.min(buffer.length, remaining);
                        int bytesRead = in.read(buffer, 0, bytesToRead);

                        if (bytesRead == -1) {
                            throw new EOFException("Connection lost while receiving file.");
                        }

                        fileOut.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }

                System.out.println("Saved as: " + outputPath.toAbsolutePath());
                System.out.println();
            }
        }
    }

    private static Path getUniquePath(Path originalPath) {
        if (!Files.exists(originalPath)) {
            return originalPath;
        }

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

        while (true) {
            Path candidate = parent.resolve(name + "_" + counter + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            counter++;
        }
    }
}