package dev.sborka.incognita.radio.voice;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import dev.sborka.incognita.Incognita;

/** Simple Voice Chat entry point. Only ever loaded by the voice chat mod, so it is safe without it. */
@ForgeVoicechatPlugin
public final class RadioPlugin implements VoicechatPlugin {
    static final String CATEGORY = "incognita_radio";

    private volatile RadioRouter router;

    @Override
    public String getPluginId() {
        return Incognita.MOD_ID + "_radio";
    }

    @Override
    public void initialize(VoicechatApi api) {
        Incognita.LOGGER.info("Simple Voice Chat found, radio voice enabled");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, e -> shutdown());
        registration.registerEvent(MicrophonePacketEvent.class, e -> {
            RadioRouter r = router;
            if (r != null) r.onMicrophone(e);
        });
        registration.registerEvent(CreateGroupEvent.class, e -> {
            RadioRouter r = router;
            if (r != null) r.onGroup(e);
        });
        registration.registerEvent(JoinGroupEvent.class, e -> {
            RadioRouter r = router;
            if (r != null) r.onGroup(e);
        });
    }

    private void onStarted(VoicechatServerStartedEvent event) {
        VoicechatServerApi api = event.getVoicechat();
        api.registerVolumeCategory(api.volumeCategoryBuilder()
            .setId(CATEGORY)
            .setName("Radio")
            .setDescription("Volume of voices received over the radio")
            .build());
        router = new RadioRouter(api);
    }

    private void shutdown() {
        RadioRouter r = router;
        router = null;
        if (r != null) r.shutdown();
    }
}
