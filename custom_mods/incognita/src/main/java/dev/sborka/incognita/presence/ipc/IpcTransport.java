package dev.sborka.incognita.presence.ipc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Byte stream of a Discord IPC pipe, on Unix sockets or Windows named pipes. */
public interface IpcTransport extends Closeable {
    InputStream in();

    OutputStream out();

    /** Opens a concrete Unix socket; used by the tests and by {@link #connectAuto()}. */
    static IpcTransport connect(Path socket) throws IOException {
        if (isWindows()) {
            return connectPipe(socket.toString());
        }
        SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            channel.connect(UnixDomainSocketAddress.of(socket));
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
        return new Streams(Channels.newInputStream(channel), Channels.newOutputStream(channel), channel);
    }

    /** Finds the first running Discord client (it opens {@code discord-ipc-0} ... {@code discord-ipc-9}). */
    static IpcTransport connectAuto() throws IOException {
        IOException last = null;
        for (String candidate : candidates()) {
            try {
                return isWindows() ? connectPipe(candidate) : connect(Path.of(candidate));
            } catch (IOException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("Discord IPC socket not found") : last;
    }

    private static IpcTransport connectPipe(String path) throws IOException {
        RandomAccessFile file = new RandomAccessFile(path, "rw");
        try {
            return new Streams(new BufferedInputStream(Channels.newInputStream(file.getChannel())),
                new BufferedOutputStream(Channels.newOutputStream(file.getChannel())), file);
        } catch (RuntimeException e) {
            file.close();
            throw e;
        }
    }

    private static List<String> candidates() {
        List<String> paths = new ArrayList<>();
        if (isWindows()) {
            for (int i = 0; i < 10; i++) {
                paths.add("\\\\.\\pipe\\discord-ipc-" + i);
            }
            return paths;
        }
        List<String> bases = new ArrayList<>();
        addEnv(bases, "XDG_RUNTIME_DIR");
        addEnv(bases, "TMPDIR");
        addEnv(bases, "TMP");
        addEnv(bases, "TEMP");
        bases.add("/tmp");
        bases.add("/run/user/" + currentUid());
        bases.add("/run/user/" + currentUid() + "/app/com.discordapp.Discord");
        bases.add("/run/user/" + currentUid() + "/snap.discord");
        for (String base : bases) {
            for (int i = 0; i < 10; i++) {
                Path path = Path.of(base, "discord-ipc-" + i);
                if (Files.exists(path)) {
                    paths.add(path.toString());
                }
            }
        }
        return paths;
    }

    private static void addEnv(List<String> bases, String name) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank() && !bases.contains(value)) {
            bases.add(value);
        }
    }

    private static String currentUid() {
        try {
            return String.valueOf(Files.getAttribute(Path.of("/proc/self"), "unix:uid"));
        } catch (IOException | UnsupportedOperationException e) {
            return System.getProperty("user.name", "");
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** A plain stream pair over a channel, a file or a named pipe. */
    record Streams(InputStream in, OutputStream out, Closeable closeable) implements IpcTransport {
        @Override
        public void close() throws IOException {
            closeable.close();
        }
    }
}
