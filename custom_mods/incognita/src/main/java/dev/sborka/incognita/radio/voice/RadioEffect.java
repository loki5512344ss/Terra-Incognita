package dev.sborka.incognita.radio.voice;

import java.util.Random;

/**
 * Walkie-talkie voice: static is mixed in first, then everything goes through a 300 Hz high-pass and a
 * 3 kHz low-pass (steep, two stages), and finally a soft clipper adds the harmonic grit.
 * Works on 48 kHz mono 16-bit frames. One instance per speaker (filters keep state).
 */
public final class RadioEffect {
    public static final float SAMPLE_RATE = 48_000f;

    private final Random random;
    private final float noise;
    private final float drive;
    private final float driveNorm;
    private final Biquad highPass = Biquad.highPass(300f, 0.707f);
    private final Biquad lowPassA = Biquad.lowPass(3000f, 0.707f);
    private final Biquad lowPassB = Biquad.lowPass(3000f, 0.707f);

    public RadioEffect(long seed, double noise, double drive) {
        this.random = new Random(seed);
        this.noise = (float) noise;
        this.drive = (float) drive;
        this.driveNorm = (float) Math.tanh(drive);
    }

    public void reset() {
        highPass.reset();
        lowPassA.reset();
        lowPassB.reset();
    }

    /** Processes a frame in place. */
    public void process(short[] pcm) {
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = toShort(chain(pcm[i] / 32768f + noiseSample()));
        }
    }

    /** The squelch "kshhk" at the start and end of a transmission: a short decaying burst of static. */
    public short[] squelch(int length) {
        short[] out = new short[length];
        for (int i = 0; i < length; i++) {
            float envelope = 1f - (float) i / length;
            float x = (random.nextFloat() * 2f - 1f) * 0.35f * envelope;
            out[i] = toShort(chain(x));
        }
        return out;
    }

    private float chain(float x) {
        float y = lowPassB.process(lowPassA.process(highPass.process(x)));
        return (float) Math.tanh(drive * y * 1.4f) / driveNorm * 0.7f;
    }

    private float noiseSample() {
        return (random.nextFloat() * 2f - 1f) * noise;
    }

    private static short toShort(float v) {
        int s = Math.round(v * 32767f);
        return (short) Math.max(-32768, Math.min(32767, s));
    }

    /** RBJ cookbook biquad. */
    static final class Biquad {
        private final float b0, b1, b2, a1, a2;
        private float x1, x2, y1, y2;

        private Biquad(float b0, float b1, float b2, float a0, float a1, float a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        static Biquad lowPass(float freq, float q) {
            double w = 2 * Math.PI * freq / SAMPLE_RATE;
            float cos = (float) Math.cos(w), alpha = (float) (Math.sin(w) / (2 * q));
            return new Biquad((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        static Biquad highPass(float freq, float q) {
            double w = 2 * Math.PI * freq / SAMPLE_RATE;
            float cos = (float) Math.cos(w), alpha = (float) (Math.sin(w) / (2 * q));
            return new Biquad((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        float process(float x) {
            float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }

        void reset() {
            x1 = x2 = y1 = y2 = 0f;
        }
    }
}
