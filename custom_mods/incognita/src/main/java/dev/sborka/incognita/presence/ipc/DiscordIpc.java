package dev.sborka.incognita.presence.ipc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.sborka.incognita.Incognita;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Minimal Discord IPC client: connects to the desktop client, performs the handshake and sends
 * {@code SET_ACTIVITY}. One instance per game session; it reconnects on its own after Discord restarts.
 *
 * <p>The pipe is used strictly in turns: write a message, then read its answer. There must be no background reader.
 * On Windows the pipe cannot be written while a read is pending (neither by the Java file channel nor by the system
 * handle), so a reader waiting for Discord blocked the next update forever. Calls block, so they must not run on the
 * game thread. Not thread safe.
 */
public final class DiscordIpc implements AutoCloseable {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;
    private static final long RETRY_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private final String clientId;
    private final IpcSource source;
    private final Gson gson = new Gson();

    private IpcTransport transport;
    private long nextAttempt;

    public DiscordIpc(String clientId) {
        this(clientId, IpcTransport::connectAuto);
    }

    DiscordIpc(String clientId, IpcSource source) {
        this.clientId = clientId;
        this.source = source;
    }

    /** Sends the card. Returns {@code false} when Discord is not running; the next call retries. */
    public boolean setActivity(DiscordActivity activity) {
        if (clientId.isBlank() || activity == null || !activity.hasContent()) {
            return false;
        }
        if (!ensureConnected()) {
            return false;
        }
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity.toJson());
        JsonObject command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.add("args", args);
        command.addProperty("nonce", UUID.randomUUID().toString());
        try {
            sendOn(transport, OP_FRAME, gson.toJson(command).getBytes(StandardCharsets.UTF_8));
            readReply(transport);
            return true;
        } catch (IOException e) {
            disconnect();
            return false;
        }
    }

    private boolean ensureConnected() {
        if (transport != null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now < nextAttempt) {
            return false;
        }
        IpcTransport opened = null;
        try {
            opened = source.open();
            sendOn(opened, OP_HANDSHAKE, gson.toJson(handshake()).getBytes(StandardCharsets.UTF_8));
            readReply(opened);
            transport = opened;
            nextAttempt = 0L;
            return true;
        } catch (IOException | RuntimeException e) {
            closeQuietly(opened);
            nextAttempt = now + RETRY_MILLIS;
            Incognita.LOGGER.debug("Discord is not reachable for Rich Presence: {}", e.toString());
            return false;
        }
    }

    private JsonObject handshake() {
        JsonObject json = new JsonObject();
        json.addProperty("v", 1);
        json.addProperty("client_id", clientId);
        return json;
    }

    /** Reads the answer to the last message, replying to pings that arrive before it. */
    private void readReply(IpcTransport target) throws IOException {
        InputStream in = target.in();
        while (true) {
            int op = readInt(in);
            int length = readInt(in);
            byte[] payload = in.readNBytes(length);
            if (payload.length != length || op == OP_CLOSE) {
                throw new EOFException("Discord closed the pipe");
            }
            if (op != OP_PING) {
                return;
            }
            sendOn(target, OP_PONG, payload);
        }
    }

    private void sendOn(IpcTransport target, int op, byte[] payload) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(op).putInt(payload.length).put(payload);
        OutputStream out = target.out();
        out.write(buffer.array());
        out.flush();
    }

    private static int readInt(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(4);
        if (bytes.length != 4) {
            throw new EOFException("Discord closed the pipe");
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private void disconnect() {
        IpcTransport current = transport;
        transport = null;
        closeQuietly(current);
    }

    private static void closeQuietly(IpcTransport target) {
        if (target == null) {
            return;
        }
        try {
            target.close();
        } catch (IOException ignored) {
            // Nothing useful to do.
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    /** Lets the tests substitute a fake socket. */
    @FunctionalInterface
    interface IpcSource {
        IpcTransport open() throws IOException;
    }
}
