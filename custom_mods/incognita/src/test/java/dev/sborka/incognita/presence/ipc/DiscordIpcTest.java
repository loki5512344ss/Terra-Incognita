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
