package com.elections.modules.broadcast;

import com.elections.infrastructure.StreamIds;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Una conexión SSE del público. El fan-out NUNCA escribe en el socket: solo encola el frame ya
 * serializado (O(1), no bloquea). Un hilo virtual por conexión vacía la cola hacia la red, de modo
 * que un cliente lento solo frena su propio hilo. "pending" mide los bytes encolados sin enviar (RF-9).
 */
public final class SseConnection {

    public record Frame(String id, String event, String data, boolean comment) {
        int size() {
            return (data == null ? 0 : data.length()) + (id == null ? 0 : id.length()) + 24;
        }
    }

    private final String id;
    private final String topic;
    private final SseEmitter emitter;
    private final LinkedBlockingQueue<Frame> queue = new LinkedBlockingQueue<>();
    private final AtomicLong pending = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Consumer<SseConnection> onClose;
    private final List<Frame> buffered = new ArrayList<>();
    private boolean initializing = true;

    public SseConnection(String id, String topic, SseEmitter emitter, Consumer<SseConnection> onClose) {
        this.id = id;
        this.topic = topic;
        this.emitter = emitter;
        this.onClose = onClose;
        emitter.onCompletion(this::close);
        emitter.onTimeout(this::close);
        emitter.onError(e -> close());
        Thread.ofVirtual().name("sse-" + id).start(this::writeLoop);
    }

    public String id() { return id; }
    public String topic() { return topic; }
    public long pendingBytes() { return pending.get(); }
    public boolean isClosed() { return closed.get(); }

    /** Lo llama el fan-out. Mientras se arma el estado inicial, los eventos en vivo esperan aparte. */
    public void offer(Frame frame) {
        synchronized (this) {
            if (initializing) {
                buffered.add(frame);
                return;
            }
        }
        enqueue(frame);
    }

    /** Frames del estado inicial (snapshot o reenvío de lo perdido): van directo a la cola. */
    public void sendInitial(Frame frame) {
        enqueue(frame);
    }

    /** Termina la inicialización: suelta los eventos en vivo que llegaron mientras tanto, sin repetir los ya cubiertos. */
    public void finishInit(String coveredUpTo) {
        synchronized (this) {
            for (Frame f : buffered) {
                if (f.id() == null || coveredUpTo == null || !StreamIds.isValid(coveredUpTo) || StreamIds.compare(f.id(), coveredUpTo) > 0) {
                    enqueue(f);
                }
            }
            buffered.clear();
            initializing = false;
        }
    }

    public void enqueue(Frame frame) {
        if (closed.get()) return;
        pending.addAndGet(frame.size());
        queue.offer(frame);
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                Frame f = queue.poll(30, TimeUnit.SECONDS);
                if (f == null) continue;
                pending.addAndGet(-f.size());
                if (f.comment()) {
                    emitter.send(SseEmitter.event().comment(f.data()));
                } else {
                    SseEmitter.SseEventBuilder b = SseEmitter.event().name(f.event()).data(f.data());
                    if (f.id() != null) b.id(f.id());
                    emitter.send(b);
                }
            }
        } catch (IOException | IllegalStateException e) {
            close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            close();
        }
    }

    /** Cierra la conexión; el cliente vuelve con su lastEventId y no pierde nada (RF-6). */
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        queue.clear();
        onClose.accept(this);
        Thread.ofVirtual().start(() -> {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // ya estaba cerrada
            }
        });
    }
}
