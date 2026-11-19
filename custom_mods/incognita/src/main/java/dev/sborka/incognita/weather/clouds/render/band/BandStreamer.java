package dev.sborka.incognita.weather.clouds.render.band;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.weather.clouds.config.CloudSettings;
import dev.sborka.incognita.weather.clouds.field.BandSpec;
import dev.sborka.incognita.weather.clouds.field.CloudField;
import dev.sborka.incognita.weather.clouds.field.CoverModel;
import dev.sborka.incognita.weather.clouds.field.mesh.BandGeometry;

import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the geometry of one band around the camera: when the camera's band-space position leaves the anchor chunk
 * (camera movement or the band's own wind), when the cover moved by more than the rebuild step or when the settings
 * changed, a new window is generated on a background thread. The previous geometry stays on screen until the new
 * one is uploaded, so a rebuild is never visible. One request per band is in flight at a time.
 */
public final class BandStreamer implements AutoCloseable {
    private final BandSpec spec;
    private final BandMesh mesh = new BandMesh();
    @Nullable private BandGeometry current;
    @Nullable private CompletableFuture<BandGeometry> pending;
    private boolean loggedFailure;

    public BandStreamer(BandSpec spec) {
        this.spec = spec;
    }

    /**
     * Uploads a finished window and requests a new one when needed. Render thread.
     *
     * @param bandX      camera position in band space (world minus the band's wind offset)
     * @param bandZ      camera position in band space
     * @param radius     window radius in blocks
     * @param threshold  live cover threshold of this band
     * @param cloudBase  world Y of the cloud base
     * @param streetAxis fixed cloud street direction of this band in radians
     * @return the geometry that is on the GPU now, or null before the first upload
     */
    @Nullable
    public BandGeometry update(double bandX, double bandZ, int radius, float threshold, float cloudBase,
                               float streetAxis) {
        collect();
        float chunk = spec.chunkSize();
        int anchorX = (int) Math.floor(bandX / chunk);
        int anchorZ = (int) Math.floor(bandZ / chunk);
        BandGeometry g = current;
        if (g != null && Math.abs(anchorX - g.anchorX()) < CloudSettings.REBUILD_CHUNKS
            && Math.abs(anchorZ - g.anchorZ()) < CloudSettings.REBUILD_CHUNKS) {
            // The window has room for some drift: keep the anchor and rebuild far less often.
            anchorX = g.anchorX();
            anchorZ = g.anchorZ();
        }
        if (pending == null && stale(anchorX, anchorZ, radius, threshold, cloudBase)) {
            CloudField.Request request = new CloudField.Request(spec, anchorX, anchorZ, radius, threshold, cloudBase,
                streetAxis);
            pending = CompletableFuture.supplyAsync(() -> CloudField.generate(request), Util.backgroundExecutor());
        }
        return current;
    }

    private void collect() {
        if (pending == null || !pending.isDone()) {
            return;
        }
        CompletableFuture<BandGeometry> done = pending;
        pending = null;
        try {
            BandGeometry geometry = done.join();
            mesh.upload(geometry.data(), geometry.puffs());
            current = geometry;
        } catch (RuntimeException e) {
            if (!loggedFailure) {
                loggedFailure = true;
                Incognita.LOGGER.error("Cloud band {} generation failed", spec.index(), e);
            }
        }
    }

    private boolean stale(int anchorX, int anchorZ, int radius, float threshold, float cloudBase) {
        BandGeometry g = current;
        if (g == null) {
            return true;
        }
        CloudField.Request built = (CloudField.Request) g.key();
        return g.anchorX() != anchorX || g.anchorZ() != anchorZ || built.radius() != radius
            || built.cloudBase() != cloudBase || CoverModel.needsRebuild(threshold, g.threshold());
    }

    public BandSpec spec() {
        return spec;
    }

    public BandMesh mesh() {
        return mesh;
    }

    @Override
    public void close() {
        mesh.close();
        current = null;
        // A running generation finishes on its own; its result is simply dropped.
        pending = null;
    }
}
