package dev.sborka.incognita.presence.ipc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Talks to a fake Discord IPC socket and checks the wire protocol. */
class DiscordIpcTest {
    @Test
    void sendsHandshakeAndActivity(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"));

        Path socket = dir.resolve("discord-ipc-0");
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<JsonObject> handshake = new AtomicReference<>();
        AtomicReference<JsonObject> activity = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        ServerSocketChannel listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        listener.bind(UnixDomainSocketAddress.of(socket));
        Thread server = new Thread(() -> {
            try (listener) {
                try (SocketChannel client = listener.accept()) {
                    DataInputStream in = new DataInputStream(java.nio.channels.Channels.newInputStream(client));
                    Frame hello = readFrame(in);
                    assertEquals(0, hello.op());
                    handshake.set(hello.json());
                    writeFrame(java.nio.channels.Channels.newOutputStream(client), 1,
                        "{\"evt\":\"READY\",\"data\":{\"v\":1}}".getBytes(StandardCharsets.UTF_8));
                    Frame command = readFrame(in);
                    assertEquals(1, command.op());
                    activity.set(command.json());
                    writeFrame(java.nio.channels.Channels.newOutputStream(client), 1,
                        "{\"cmd\":\"SET_ACTIVITY\"}".getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        }, "fake-discord");
        server.setDaemon(true);
        server.start();

        try (DiscordIpc ipc = new DiscordIpc("1574849152070848593", () -> IpcTransport.connect(socket))) {
            DiscordActivity payload = new DiscordActivity("", "Ищет путь без координат", 1_700_000_000_000L,
                "logo", "Terra Incognita", "GitHub", "https://github.com/loki5512344ss/Terra-Incognita");
            assertTrue(ipc.setActivity(payload));
            assertTrue(done.await(5, TimeUnit.SECONDS), "fake Discord did not finish");
        }

        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertEquals("1574849152070848593", handshake.get().get("client_id").getAsString());
        assertEquals(1, handshake.get().get("v").getAsInt());

        JsonObject args = activity.get().getAsJsonObject("args");
        assertEquals("SET_ACTIVITY", activity.get().get("cmd").getAsString());
        assertEquals(ProcessHandle.current().pid(), args.get("pid").getAsLong());
        JsonObject act = args.getAsJsonObject("activity");
        assertTrue(!act.has("details"));
        assertEquals("Ищет путь без координат", act.get("state").getAsString());
        assertEquals(1_700_000_000_000L, act.getAsJsonObject("timestamps").get("start").getAsLong());
        JsonObject assets = act.getAsJsonObject("assets");
        assertEquals("logo", assets.get("large_image").getAsString());
        assertEquals("Terra Incognita", assets.get("large_text").getAsString());
        JsonObject button = act.getAsJsonArray("buttons").get(0).getAsJsonObject();
        assertEquals("GitHub", button.get("label").getAsString());
    }

    /** Like the real client, this one answers every message and then stays silent until the next one. */
    @Test
    void laterUpdatesDoNotBlockWhileDiscordIsSilent(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"));
        Path socket = dir.resolve("discord-ipc-0");
        ServerSocketChannel listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        listener.bind(UnixDomainSocketAddress.of(socket));
        Thread server = new Thread(() -> {
            try (listener; SocketChannel client = listener.accept()) {
                DataInputStream in = new DataInputStream(java.nio.channels.Channels.newInputStream(client));
                while (true) {
                    readFrame(in);
                    ByteBuffer reply = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
                    reply.putInt(1).putInt(2).put("{}".getBytes(StandardCharsets.UTF_8)).flip();
                    client.write(reply);
                }
            } catch (IOException ignored) {
                // the client closed the socket at the end of the test
            }
        }, "silent-discord");
        server.setDaemon(true);
        server.start();

        DiscordIpc ipc = new DiscordIpc("123", () -> IpcTransport.connect(socket));
        for (int i = 0; i < 3; i++) {
            FutureTask<Boolean> update = new FutureTask<>(
                () -> ipc.setActivity(new DiscordActivity("", "state", 0L, "", "", "", "")));
            Thread game = new Thread(update, "fake-render-thread");
            game.setDaemon(true);
            game.start();
            assertTrue(update.get(3, TimeUnit.SECONDS), "update " + i + " failed");
            Thread.sleep(300); // gives a background reader, if there is one, time to block on the socket
        }
        ipc.close();
    }

    @Test
    void updatesFinishOnAPipeThatCannotReadAndWriteAtOnce() throws Exception {
        try (DiscordIpc ipc = new DiscordIpc("123", HalfDuplexPipe::new)) {
            for (int i = 0; i < 3; i++) {
                FutureTask<Boolean> update = new FutureTask<>(
                    () -> ipc.setActivity(new DiscordActivity("", "state", 0L, "", "", "", "")));
                Thread caller = new Thread(update, "caller");
                caller.setDaemon(true);
                caller.start();
                assertTrue(update.get(3, TimeUnit.SECONDS), "update " + i + " failed");
                Thread.sleep(200);
            }
        }
    }

    /**
     * Behaves like a Windows named pipe opened as a file: a write waits while a read is pending. Like Discord, it
     * answers every frame it gets and says nothing otherwise.
     */
    private static final class HalfDuplexPipe implements IpcTransport {
        private final Object handle = new Object();
        private final java.util.concurrent.BlockingQueue<Integer> replies =
            new java.util.concurrent.LinkedBlockingQueue<>();

        @Override
        public java.io.InputStream in() {
            return new java.io.InputStream() {
                @Override
                public int read() throws IOException {
                    synchronized (handle) {
                        try {
                            return replies.take();
                        } catch (InterruptedException e) {
                            throw new IOException(e);
                        }
                    }
                }
            };
        }

        @Override
        public java.io.OutputStream out() {
            return new java.io.OutputStream() {
                @Override
                public void write(int b) {
                    write(new byte[] {(byte) b}, 0, 1);
                }

                @Override
                public void write(byte[] bytes, int offset, int length) {
                    synchronized (handle) {
                        ByteBuffer reply = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
                        reply.putInt(1).putInt(2).put("{}".getBytes(StandardCharsets.UTF_8));
                        for (byte value : reply.array()) {
                            replies.add(value & 0xFF);
                        }
                    }
                }
            };
        }

        @Override
        public void close() {
            // nothing to release
        }
    }

    @Test
    void rejectsEmptyActivity() {
        try (DiscordIpc ipc = new DiscordIpc("123", () -> {
            throw new IOException("must not connect");
        })) {
            assertTrue(!ipc.setActivity(new DiscordActivity("", "", 0L, "", "", "", "")));
        }
    }

    @Test
    void missingSocketIsNotFatal(@TempDir Path dir) {
        try (DiscordIpc ipc = new DiscordIpc("123", () -> IpcTransport.connect(dir.resolve("nope")))) {
            assertTrue(!ipc.setActivity(new DiscordActivity("a", "b", 0L, "", "", "", "")));
        }
    }

    private static Frame readFrame(DataInputStream in) throws IOException {
        int op = Integer.reverseBytes(in.readInt());
        int length = Integer.reverseBytes(in.readInt());
        byte[] payload = new byte[length];
        in.readFully(payload);
        return new Frame(op, JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject());
    }

    private record Frame(int op, JsonObject json) {}

    private static void writeFrame(java.io.OutputStream out, int op, byte[] payload) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op).putInt(payload.length).put(payload);
        out.write(buf.array());
        out.flush();
    }
}
