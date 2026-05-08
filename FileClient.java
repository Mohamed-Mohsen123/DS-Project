import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Scanner;

public class FileClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length >= 1) {
            host = args[0];
        }

        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }

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

            while (true) {
                System.out.print("File path: ");
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    out.writeUTF("__END__");
                    out.flush();
                    System.out.println("Done sending files.");
                    break;
                }

                Path filePath = Paths.get(input);

                if (!Files.exists(filePath)) {
                    System.out.println("File does not exist.");
                    continue;
                }

                if (!Files.isRegularFile(filePath)) {
                    System.out.println("That path is not a regular file.");
                    continue;
                }

                sendFile(filePath, out);
            }

        } catch (ConnectException e) {
            System.err.println("Could not connect to server. Is FileServer running?");
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }

    private static void sendFile(Path filePath, DataOutputStream out) throws IOException {
        String fileName = filePath.getFileName().toString();
        long fileSize = Files.size(filePath);

        System.out.println("Sending: " + fileName);
        System.out.println("Size: " + fileSize + " bytes");

        out.writeUTF(fileName);
        out.writeLong(fileSize);

        try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(filePath))) {
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        out.flush();

        System.out.println("Sent successfully.");
        System.out.println();
    }
}