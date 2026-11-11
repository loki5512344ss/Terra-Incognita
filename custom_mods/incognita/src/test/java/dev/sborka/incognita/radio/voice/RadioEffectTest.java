package dev.sborka.incognita.radio.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RadioEffectTest {
    private static short[] tone(double hz, int samples) {
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = (short) (12000 * Math.sin(2 * Math.PI * hz * i / RadioEffect.SAMPLE_RATE));
        }
        return out;
    }

    /** RMS of the last part of a long run, after the filters have settled. */
    private static double settledRms(double hz) {
        RadioEffect fx = new RadioEffect(1, 0.0, 1.0);
        short[] pcm = tone(hz, 48_000);
        fx.process(pcm);
        double sum = 0;
        int n = 0;
        for (int i = 24_000; i < pcm.length; i++) {
            sum += (double) pcm[i] * pcm[i];
            n++;
        }
        return Math.sqrt(sum / n);
    }

    @Test
    void bassIsCutAndMidsPass() {
        assertTrue(settledRms(1000) > 4 * settledRms(60), "1 kHz should be far louder than 60 Hz");
    }

    @Test
    void trebleIsCut() {
        assertTrue(settledRms(1000) > 4 * settledRms(10_000), "1 kHz should be far louder than 10 kHz");
    }

    @Test
    void outputStaysInRangeEvenWhenDrivenHard() {
        RadioEffect fx = new RadioEffect(2, 0.5, 10.0);
        short[] pcm = new short[960];
        java.util.Arrays.fill(pcm, Short.MAX_VALUE);
        fx.process(pcm);
        for (short s : pcm) {
            assertTrue(s >= Short.MIN_VALUE && s <= Short.MAX_VALUE);
        }
    }

    @Test
    void sameSeedGivesSameStatic() {
        short[] a = new RadioEffect(7, 0.05, 2.5).squelch(960);
        short[] b = new RadioEffect(7, 0.05, 2.5).squelch(960);
        assertArrayEquals(a, b);
    }

    @Test
    void squelchIsAudibleAndFadesOut() {
        short[] s = new RadioEffect(3, 0.0, 2.5).squelch(960);
        double head = 0, tail = 0;
        for (int i = 0; i < 240; i++) {
            head += Math.abs(s[i]);
            tail += Math.abs(s[s.length - 1 - i]);
        }
        assertTrue(head > 0);
        assertTrue(head > tail);
    }
}
