package dev.sborka.incognita.weather.clouds.config;

/**
 * Snapshot of the configured values for one frame, so a frame never sees the config change halfway through.
 *
 * @param drawDistance how far clouds are generated and drawn, in blocks
 * @param baseOffset   blocks added to the dimension cloud height
 * @param density      opacity of a dense cloud core
 * @param forceLegacy  whether the OpenGL 3.3 path is forced
 * @param halfResolution whether the cloud passes run at half resolution
 */
public record CloudSettings(int drawDistance, float baseOffset, float density, boolean forceLegacy,
                            boolean halfResolution) {
    /** Values from the config file. */
    public static CloudSettings fromConfig() {
        return new CloudSettings(CloudConfig.drawDistance(), (float) CloudConfig.baseOffset(),
            (float) CloudConfig.density(), CloudConfig.forceLegacyPath(), CloudConfig.halfResolution());
    }

    /** Generation radius of a band window: the draw distance plus room for the largest heap and a chunk of drift. */
    public int generationRadius(float chunkSize) {
        return drawDistance + (int) Math.ceil(chunkSize * REBUILD_CHUNKS) + 80;
    }

    /** How many chunks the camera may move from the window anchor before the window is rebuilt. */
    public static final float REBUILD_CHUNKS = 2.0f;
}
