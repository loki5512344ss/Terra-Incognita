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
    private final Object writeLock = new Object();

    private volatile IpcTransport transport;
    private volatile boolean connected;
    private volatile long nextAttempt;
    private Thread reader;

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
            sendFrame(OP_FRAME, gson.toJson(command).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            disconnect();
            return false;
        }
    }

    private boolean ensureConnected() {
        IpcTransport current = transport;
        if (current != null && connected) {
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
            transport = opened;
            connected = true;
            startReader(opened);
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

    private void startReader(IpcTransport opened) {
        Thread thread = new Thread(() -> readLoop(opened), "incognita-discord-rpc");
        thread.setDaemon(true);
        reader = thread;
        thread.start();
    }

    private void readLoop(IpcTransport opened) {
        try {
            InputStream in = opened.in();
            while (connected && transport == opened) {
                int op = readInt(in);
                int length = readInt(in);
                byte[] payload = in.readNBytes(length);
                if (payload.length != length) {
                    break;
                }
                if (op == OP_PING) {
                    sendOn(opened, OP_PONG, payload);
                } else if (op == OP_CLOSE) {
                    break;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Discord closed the pipe; the next setActivity reconnects.
        } finally {
            if (transport == opened) {
                disconnect();
            }
        }
    }

    private void sendFrame(int op, byte[] payload) throws IOException {
        IpcTransport current = transport;
        if (current == null) {
            throw new IOException("not connected");
        }
        sendOn(current, op, payload);
    }

    private void sendOn(IpcTransport target, int op, byte[] payload) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(op).putInt(payload.length).put(payload);
        synchronized (writeLock) {
            OutputStream out = target.out();
            out.write(buffer.array());
            out.flush();
        }
    }

    private static int readInt(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(4);
        if (bytes.length != 4) {
            throw new EOFException("Discord closed the pipe");
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private void disconnect() {
        connected = false;
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
        Thread thread = reader;
        if (thread != null) {
            thread.interrupt();
            reader = null;
        }
    }

    /** Lets the tests substitute a fake socket. */
    @FunctionalInterface
    interface IpcSource {
        IpcTransport open() throws IOException;
    }
}
