package com.codex.maanikke.rootprobe;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.OutputStream;

final class PreviewFrameServer {
    private final FrameCaptureBackend capture;
    private final ProbeLogger logger;
    private final Thread thread;
    private volatile boolean running = false;
    private LocalServerSocket serverSocket;
    private int clientSerial = 0;

    PreviewFrameServer(FrameCaptureBackend capture, ProbeLogger logger) {
        this.capture = capture;
        this.logger = logger;
        this.thread = new Thread(new Runnable() {
            @Override
            public void run() {
                serve();
            }
        }, "maanikke-preview-local-socket");
    }

    void start() {
        running = true;
        thread.start();
    }

    void close() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Throwable ignored) {
            }
        }
        try {
            thread.join(1200);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private void serve() {
        try {
            serverSocket = new LocalServerSocket(ProbeConfig.PREVIEW_SOCKET_NAME);
            logger.log("preview local socket listening name=" + ProbeConfig.PREVIEW_SOCKET_NAME);
            while (running) {
                final LocalSocket socket = serverSocket.accept();
                final int serial = ++clientSerial;
                Thread clientThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        handle(socket, serial);
                    }
                }, "maanikke-preview-client-" + serial);
                clientThread.setDaemon(true);
                clientThread.start();
            }
        } catch (Throwable error) {
            if (running) {
                logger.log("preview local socket failed: " + error.getClass().getName() + ": " + error.getMessage());
            }
        } finally {
            logger.log("preview local socket stopped");
        }
    }

    private void handle(LocalSocket socket, int serial) {
        long lastFrameWriteAt = System.currentTimeMillis();
        long lastHeartbeatAt = 0;
        try {
            socket.setSoTimeout(1200);
            OutputStream output = socket.getOutputStream();
            long lastSequence = -1;
            while (running) {
                long now = System.currentTimeMillis();
                if (now - lastFrameWriteAt > ProbeConfig.PREVIEW_CLIENT_IDLE_TIMEOUT_MS) {
                    logger.log("preview client idle timeout serial=" + serial);
                    return;
                }
                long sequence = capture.getLatestJpegSequence();
                boolean newFrame = sequence != lastSequence;
                boolean heartbeat = now - lastHeartbeatAt >= ProbeConfig.PREVIEW_CLIENT_HEARTBEAT_MS;
                if (newFrame || heartbeat) {
                    byte[] frame = capture.getLatestJpeg();
                    writeInt(output, (int) sequence);
                    writeInt(output, frame.length);
                    if (frame.length > 0) {
                        output.write(frame);
                    }
                    output.flush();
                    if (newFrame) {
                        lastSequence = sequence;
                        lastFrameWriteAt = System.currentTimeMillis();
                    }
                    lastHeartbeatAt = System.currentTimeMillis();
                }
                try {
                    Thread.sleep(ProbeConfig.PREVIEW_FRAME_MIN_INTERVAL_MS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    static LocalSocket connectClient() throws Exception {
        LocalSocket socket = new LocalSocket();
        socket.connect(new LocalSocketAddress(
                ProbeConfig.PREVIEW_SOCKET_NAME,
                LocalSocketAddress.Namespace.ABSTRACT
        ));
        socket.setSoTimeout(500);
        return socket;
    }

    static void writeInt(OutputStream output, int value) throws Exception {
        output.write((value >>> 24) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
    }
}
