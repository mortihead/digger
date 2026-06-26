package org.digger.app;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Emulates the PC Speaker sound output using Java Sound API.
 * Generates real-time square wave audio based on Intel 8253 timer divisor values.
 *
 * Original PC Speaker frequencies derived from timer divisor:
 *   frequency = 1193180 / divisor (Hz)
 * where 1193180 Hz is the 8253 timer input clock frequency.
 *
 * Speaker modes (spkrmode):
 *   0 - Timer 2 drives speaker (SFX)
 *   1 - Timer 0 AND Timer 2 (music, modulated)
 *   2 - Timer 0 drives speaker (music/SFX with volume control)
 */
class SoundEngine {

    private static final int CRYSTAL_FREQ = 1193180;
    private static final int SAMPLE_RATE = 44100;
    private static final int UPDATE_RATE_HZ = 73;
    private static final int BUFFER_SIZE = SAMPLE_RATE / UPDATE_RATE_HZ;
    private static final double MASTER_VOLUME = 0.25;
    private static final double MIN_AUDIBLE_FREQ = 20.0;
    private static final double MAX_AUDIBLE_FREQ = 20000.0;

    private SourceDataLine line;
    private volatile boolean running;
    private Thread audioThread;
    private Thread updateThread;

    private volatile int currentT0Val = 0x7d00;
    private volatile int currentT2Val = 40;
    private volatile int currentSpkrMode = 0;
    private volatile int currentPulseWidth = 1;

    private final Sound sound;
    private double phase;

    SoundEngine(Sound sound) {
        this.sound = sound;
    }

    boolean start() {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                return false;
            }
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, BUFFER_SIZE * 4);
            line.start();
        } catch (LineUnavailableException e) {
            return false;
        }

        running = true;
        startAudioThread();
        startUpdateThread();
        return true;
    }

    void stop() {
        running = false;
        if (updateThread != null) updateThread.interrupt();
        if (audioThread != null) audioThread.interrupt();
        if (line != null) {
            try { line.drain(); line.close(); } catch (Exception ignored) {}
        }
    }

    void updateT0Val(int val) { currentT0Val = val; }
    void updateT2Val(int val) { currentT2Val = val; }
    void updateSpkrMode(int mode) { currentSpkrMode = mode; }
    void updatePulseWidth(int pw) { currentPulseWidth = pw; }

    private void startAudioThread() {
        audioThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (running) {
                generateSamples(buffer);
                int offset = 0;
                while (offset < buffer.length && running) {
                    offset += line.write(buffer, offset, buffer.length - offset);
                }
            }
        }, "Digger-Audio");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    private void startUpdateThread() {
        updateThread = new Thread(() -> {
            long intervalNanos = 1_000_000_000L / UPDATE_RATE_HZ;
            long nextTime = System.nanoTime();
            while (running) {
                sound.soundInt();
                nextTime += intervalNanos;
                long sleepNanos = nextTime - System.nanoTime();
                if (sleepNanos > 0) {
                    try {
                        Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "Digger-SoundUpdate");
        updateThread.setDaemon(true);
        updateThread.start();
    }

    private void generateSamples(byte[] buffer) {
        int t0 = currentT0Val;
        int t2 = currentT2Val;
        int mode = currentSpkrMode;
        int pw = Math.max(currentPulseWidth, 1);

        double frequency;
        double amplitude;

        if (mode == 0) {
            // Timer 2 mode (SFX) - full volume square wave
            frequency = CRYSTAL_FREQ / (double) t2;
            amplitude = MASTER_VOLUME;
        } else {
            // Timer 0 mode (Music) - volume controlled by pulsewidth
            frequency = CRYSTAL_FREQ / (double) t0;
            amplitude = Math.min(pw / 50.0, 1.0) * MASTER_VOLUME;
        }

        // Out of audible range = silence
        if (frequency < MIN_AUDIBLE_FREQ || frequency > MAX_AUDIBLE_FREQ) {
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] = 0;
            }
            phase = 0;
            return;
        }

        double phaseInc = frequency / SAMPLE_RATE;
        for (int i = 0; i < buffer.length; i++) {
            double sample = (phase < 0.5) ? amplitude : -amplitude;
            buffer[i] = (byte) Math.round(sample * 127.0);
            phase = (phase + phaseInc) % 1.0;
        }
    }
}
