package com.elections.modules.subscriptions.internal.service;

import com.elections.modules.subscriptions.SubscriptionsApi;
import com.elections.modules.subscriptions.internal.repository.SubscriptionsRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SubscriptionsService implements SubscriptionsApi {

    private final SubscriptionsRepository subscriptionsRepository;

    public SubscriptionsService(SubscriptionsRepository subscriptionsRepository) {
        this.subscriptionsRepository = subscriptionsRepository;
    }

    @Override
    public void subscribe(String sessionId, String topic) {
        subscriptionsRepository.addSubscription(sessionId, topic);
    }

    @Override
    public void unsubscribe(String sessionId, String topic) {
        subscriptionsRepository.removeSubscription(sessionId, topic);
    }

    @Override
    public void removeSession(String sessionId) {
        subscriptionsRepository.removeSession(sessionId);
    }

    @Override
    public Set<String> getSubscribers(String topic) {
        return subscriptionsRepository.getSubscribersByTopic(topic);
    }
}

