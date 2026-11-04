package com.elections.modules.broadcast.internal.repository;

import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentLinkedQueue;

@Repository
public class BroadcastBufferRepository {

    private final ConcurrentLinkedQueue<Object> dispatchBuffer = new ConcurrentLinkedQueue<>();

    public void enqueue(Object item) {
        dispatchBuffer.offer(item);
    }

    public Object dequeue() {
        return dispatchBuffer.poll();
    }

    public boolean isEmpty() {
        return dispatchBuffer.isEmpty();
    }
}

