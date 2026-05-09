# 📁 Java File Transfer — FileServer & FileClient

A simple Java program that lets you **send files from one computer to another** over a network.  
Think of it like a very basic version of file sharing — one machine acts as the **receiver (server)**, and the other acts as the **sender (client)**.

---

## 🗂️ Project Structure

```
project/
├── FileServer.java   → Runs on the receiving machine. Waits for files and saves them.
├── FileClient.java   → Runs on the sending machine. Sends files to the server.
```

---

## 🔄 How It Works — Step by Step

Here is the full sequence of what happens when you use this program:

```
[Client Machine]                          [Server Machine]
      |                                          |
      |   1. User starts FileServer             |
      |   ─────────────────────────────────>    |
      |                                  Server opens port 5000
      |                                  Creates "received_files/" folder
      |                                  Waits for a connection...
      |                                          |
      |   2. User starts FileClient             |
      |   TCP connection established ─────────> |
      |                                  "Client connected!"
      |                                          |
      |   3. User types a file path             |
      |      e.g. /home/user/photo.jpg          |
      |                                          |
      |   4. Client sends:                       |
      |      → filename  ("photo.jpg")  ──────> |
      |      → file size (in bytes)    ──────> |
      |      → raw file bytes          ──────> |
      |                                  Server reads bytes
      |                                  Writes file to disk
      |                                  Saves as "received_files/photo.jpg"
      |                                          |
      |   5. Repeat for more files...            |
      |                                          |
      |   6. User presses Enter on empty line   |
      |      Client sends "__END__" ──────────> |
      |                                  Server sees __END__, stops reading
      |                                  Connection closes
```

---

## 🧱 Architecture Overview

### `FileServer.java` — The Receiver

| Responsibility                          | How it's done                                                              |
| --------------------------------------- | -------------------------------------------------------------------------- |
| Listen for incoming connections         | `ServerSocket` on port 5000                                                |
| Accept multiple clients (one at a time) | `while(true)` loop calling `serverSocket.accept()`                         |
| Read incoming file data                 | `DataInputStream` reads filename → size → bytes                            |
| Save files safely                       | Strips any folder path from filename to prevent **path traversal attacks** |
| Handle duplicate filenames              | `getUniquePath()` renames to `file_1.txt`, `file_2.txt`, etc.              |

### `FileClient.java` — The Sender

| Responsibility                | How it's done                                     |
| ----------------------------- | ------------------------------------------------- |
| Connect to the server         | `Socket(host, port)`                              |
| Ask user for file paths       | `Scanner` reads from keyboard input               |
| Validate files before sending | Checks that the path exists and is a regular file |
| Send file data                | `DataOutputStream` writes filename → size → bytes |
| Signal when done              | Sends the special string `"__END__"`              |

---

## 🔌 The Communication Protocol (How They Talk)

The client and server follow a simple custom protocol over a single TCP connection:

```
For each file:
┌─────────────────────────────────────────┐
│  writeUTF(fileName)   — file name       │
│  writeLong(fileSize)  — size in bytes   │
│  write(bytes...)      — actual content  │
└─────────────────────────────────────────┘

When done:
┌─────────────────────┐
│  writeUTF("__END__")│
└─────────────────────┘
```

> ✅ `DataOutputStream` / `DataInputStream` are used so both sides agree on the exact binary format of the data.

---

## 🚀 How to Run

### Step 1 — Compile both files

```bash
javac FileServer.java
javac FileClient.java
```

### Step 2 — Start the server (on the receiving machine)

```bash
java FileServer
# Uses default port 5000

# OR specify a custom port:
java FileServer 8080
```

You'll see:

```
File server started.
Listening on port: 5000
Saving files to: /your/path/received_files
```

### Step 3 — Start the client (on the sending machine)

```bash
java FileClient
# Connects to localhost:5000 by default

# OR specify host and port:
java FileClient 192.168.1.10 5000
```

### Step 4 — Type file paths to send

```
Connected to server: localhost:5000
Enter file paths to send.
Press Enter on an empty line when finished.

File path: /home/user/documents/report.pdf
Sending: report.pdf
Size: 204800 bytes
Sent successfully.

File path: /home/user/photo.png
Sending: photo.png
Size: 512000 bytes
Sent successfully.

File path:        ← (press Enter on empty line to finish)
Done sending files.
```

---

## 📂 Where Are Files Saved?

The server saves all received files into a folder called **`received_files/`**, created automatically in the directory where you run the server.

If a file with the same name already exists, the server **renames it automatically**:

```
received_files/
├── report.pdf        ← first upload
├── report_1.pdf      ← second upload with same name
├── report_2.pdf      ← third upload with same name
```

---

## 🛡️ Security Note

The server strips any directory components from the filename sent by the client.  
So even if a malicious client sends `"../../etc/passwd"` as the filename, only `"passwd"` is used — the file is always saved inside `received_files/`.

---

## ⚙️ Configuration Summary

| Setting        | Default           | How to Change                                               |
| -------------- | ----------------- | ----------------------------------------------------------- |
| Server port    | `5000`            | Pass as first argument: `java FileServer 8080`              |
| Client host    | `localhost`       | Pass as first argument: `java FileClient 192.168.1.5`       |
| Client port    | `5000`            | Pass as second argument: `java FileClient 192.168.1.5 8080` |
| Save directory | `received_files/` | Edit `SAVE_DIRECTORY` in `FileServer.java`                  |

---

## 📋 Requirements

- Java 8 or higher
- Both machines on the same network (or server accessible via IP/hostname)
- Port 5000 open on the server machine (check firewall settings if needed)

---

## 🔬 Code Walkthrough — Phase by Phase

Here is a detailed explanation of what each phase of the code does, in execution order.

### ① Startup

Both programs begin in `main(args[])` and parse command-line arguments to get the host and port. The server also calls `Files.createDirectories()` to ensure the `received_files/` folder exists before any client connects.

### ② TCP Connection

The client calls `new Socket(host, port)`, which triggers the OS to complete a TCP handshake with the server. The server has been blocking on `serverSocket.accept()` — it does nothing until a client shows up. Once connected, both sides wrap the raw byte stream:

- Client → `DataOutputStream` (for writing)
- Server → `DataInputStream` (for reading)

These two stream types speak the same binary dialect, which is what makes them interoperable across the network.

### ③ User Input

The client enters a `while(true)` loop prompting the user for file paths via `Scanner`. Before sending anything, it validates each path:

- Does the file exist? (`Files.exists()`)
- Is it a regular file, not a directory? (`Files.isRegularFile()`)

The server is waiting silently the whole time.

### ④ The Wire Protocol — 3 Messages Per File

This is the core of the design. For each file, the client sends three things in strict order:

```
1. out.writeUTF(fileName)     → a length-prefixed UTF-8 string
2. out.writeLong(fileSize)    → an 8-byte integer (tells server how many bytes to expect)
3. out.write(buffer, 0, n)    → raw file bytes in 8 KB chunks
```

The server reads them in the same order:

```
1. in.readUTF()               → receive the filename
2. in.readLong()              → know exactly when to stop reading bytes
3. fileOut.write(buffer)      → loop until remaining == 0
```

The `fileSize` long is critical — the server uses it to count down `remaining` bytes and never guesses when the file ends. If the connection drops mid-transfer, `in.read()` returns `-1` and the server throws an `EOFException`.

### ⑤ Save to Disk

Before writing, the server runs two safety checks inside `handleClient()`:

1. **Path traversal prevention** — `Paths.get(fileName).getFileName()` strips any directory components. A malicious filename like `../../etc/passwd` becomes just `passwd`.
2. **Duplicate handling** — `getUniquePath()` checks if the file already exists. If so, it appends `_1`, `_2`, etc. until it finds a free name.

The file is written with `StandardOpenOption.CREATE_NEW`, which fails atomically if the file was somehow created between the check and the write.

### ⑥ Termination Signal

When the user presses Enter on an empty line, the client sends the sentinel string `"__END__"` instead of a filename. The server checks for this exact string at the top of every loop iteration:

```java
if ("__END__".equals(fileName)) { break; }
```

This cleanly ends the file-receiving loop and closes the connection.

### ⑦ Server Loops for Next Client

After a client disconnects, the server's outer `while(true)` loop calls `serverSocket.accept()` again and waits for the next client. The server never shuts down on its own — it must be stopped manually (e.g. `Ctrl+C`).

## Snapshots of Results

Below are snapshots of the results:

![Snapshot 1](./1.jpeg)

![Snapshot 2](./2.jpeg)
