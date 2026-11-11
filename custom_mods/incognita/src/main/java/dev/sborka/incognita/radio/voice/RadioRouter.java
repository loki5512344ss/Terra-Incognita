package dev.sborka.incognita.radio.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import dev.sborka.incognita.radio.RadioConfig;
import dev.sborka.incognita.radio.link.RadioDirectory;
import dev.sborka.incognita.radio.link.RadioDirectory.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voice routing for radios. Runs on the voice thread, so it only reads {@link RadioDirectory}.
 * A speaker who holds a switched-on radio is heard (through the radio effect) by everyone whose switched-on
 * radio is tuned to the same frequency, at any distance. Listeners within normal voice range hear the real
 * voice anyway and are skipped. Groups need a radio in the inventory.
 */
final class RadioRouter {
    private static final long IDLE_MILLIS = 30_000;
    private static final int SQUELCH_SAMPLES = 960;

    private final VoicechatServerApi api;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    RadioRouter(VoicechatServerApi api) {
        this.api = api;
    }

    private final class Session {
        final OpusDecoder decoder = api.createDecoder();
        final OpusEncoder encoder = api.createEncoder();
        final StaticAudioChannel channel;
        final RadioEffect effect = new RadioEffect(System.nanoTime(), RadioConfig.effectNoise(), RadioConfig.effectDrive());
        boolean open;
        long lastUsed = System.currentTimeMillis();

        Session(UUID speaker) {
            channel = api.createStaticAudioChannel(UUID.nameUUIDFromBytes(("incognita_radio:" + speaker).getBytes()));
            channel.setBypassGroupIsolation(true);
            channel.setCategory(RadioPlugin.CATEGORY);
        }

        void send(short[] pcm) {
            channel.send(encoder.encode(pcm));
        }

        void end() {
            if (open) {
                send(effect.squelch(SQUELCH_SAMPLES));
                channel.flush();
                open = false;
            }
            decoder.resetState();
            encoder.resetState();
            effect.reset();
        }

        void close() {
            decoder.close();
            encoder.close();
        }
    }

    void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || sender.getPlayer() == null) {
            return;
        }
        UUID id = sender.getPlayer().getUuid();
        Entry me = RadioDirectory.get(id);
        pruneIdle(id);

        if (RadioConfig.groupsNeedRadio() && sender.isInGroup() && (me == null || !me.hasRadio())) {
            sender.setGroup(null);
        }
        if (me == null || me.transmitFreq() < 0) {
            endSession(id);
            return;
        }
        byte[] data = event.getPacket().getOpusEncodedData();
        if (data.length == 0) {
            endSession(id);
            return;
        }
        List<VoicechatConnection> targets = targets(me);
        Session session = sessions.computeIfAbsent(id, Session::new);
        session.lastUsed = System.currentTimeMillis();
        if (targets.isEmpty()) {
            session.end();
            return;
        }
        short[] pcm = session.decoder.decode(data);
        if (RadioConfig.effect()) {
            session.effect.process(pcm);
        }
        session.channel.clearTargets();
        targets.forEach(session.channel::addTarget);
        if (!session.open) {
            session.open = true;
            if (RadioConfig.effect()) {
                session.send(session.effect.squelch(SQUELCH_SAMPLES));
            }
        }
        session.send(pcm);
    }

    private List<VoicechatConnection> targets(Entry speaker) {
        double hearing = api.getVoiceChatDistance();
        List<VoicechatConnection> out = new ArrayList<>();
        for (Entry e : RadioDirectory.snapshot().values()) {
            if (e.id().equals(speaker.id()) || !e.receiveFreqs().contains(speaker.transmitFreq())) {
                continue;
            }
            if (e.dimension().equals(speaker.dimension()) && e.distanceSqr(speaker) < hearing * hearing) {
                continue;
            }
            VoicechatConnection c = api.getConnectionOf(e.id());
            if (c != null && c.isConnected() && !c.isDisabled()) {
                out.add(c);
            }
        }
        return out;
    }

    void onGroup(CreateGroupEvent event) {
        blockWithoutRadio(event.getConnection(), event::cancel);
    }

    void onGroup(JoinGroupEvent event) {
        blockWithoutRadio(event.getConnection(), event::cancel);
    }

    private void blockWithoutRadio(VoicechatConnection connection, Runnable cancel) {
        if (!RadioConfig.groupsNeedRadio() || connection == null || connection.getPlayer() == null) {
            return;
        }
        Entry e = RadioDirectory.get(connection.getPlayer().getUuid());
        if (e == null || !e.hasRadio()) {
            cancel.run();
        }
    }

    private void endSession(UUID id) {
        Session s = sessions.get(id);
        if (s != null) {
            s.end();
        }
    }

    private void pruneIdle(UUID except) {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(en -> {
            if (!en.getKey().equals(except) && now - en.getValue().lastUsed > IDLE_MILLIS) {
                en.getValue().close();
                return true;
            }
            return false;
        });
    }

    void shutdown() {
        sessions.values().forEach(Session::close);
        sessions.clear();
    }
}
