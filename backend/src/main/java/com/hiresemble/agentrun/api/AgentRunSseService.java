package com.hiresemble.agentrun.api;

import com.hiresemble.agentrun.application.AgentRunApplicationService;
import com.hiresemble.agentrun.application.AgentRunCommittedEvent;
import com.hiresemble.agentrun.application.AgentRunSnapshot;
import com.hiresemble.agentrun.infrastructure.AgentRunEventBus;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentRunSseService {

    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    private final AgentRunApplicationService applicationService;
    private final AgentRunEventBus eventBus;
    private final AgentRunApiMapper mapper;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Subscriber> subscribers = new ConcurrentHashMap<>();

    public AgentRunSseService(
            AgentRunApplicationService applicationService,
            AgentRunEventBus eventBus,
            AgentRunApiMapper mapper,
            Clock clock) {
        this.applicationService = applicationService;
        this.eventBus = eventBus;
        this.mapper = mapper;
        this.clock = clock;
    }

    public SseEmitter open(UUID userId, UUID agentRunId) {
        // Validate owner before a streaming response can be committed.
        applicationService.detail(userId, agentRunId);
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        UUID subscriberId = UUID.randomUUID();
        Subscriber subscriber = new Subscriber(subscriberId, emitter);
        subscribers.put(subscriberId, subscriber);
        AgentRunEventBus.Subscription subscription = eventBus.subscribe(agentRunId, subscriber::onEvent);
        subscriber.bind(subscription);
        emitter.onCompletion(subscriber::close);
        emitter.onTimeout(subscriber::close);
        emitter.onError(ignored -> subscriber.close());
        try {
            // Register first, then read the DB. Events racing with the read remain buffered.
            AgentRunSnapshot snapshot = applicationService.detail(userId, agentRunId);
            subscriber.initialize(snapshot);
        } catch (RuntimeException exception) {
            subscriber.close();
            throw exception;
        }
        return emitter;
    }

    @Scheduled(fixedDelayString = "${hiresemble.agent-runtime.heartbeat-interval:15s}")
    public void sendTransportHeartbeats() {
        Instant now = clock.instant();
        for (Subscriber subscriber : subscribers.values()) {
            subscriber.heartbeat(now);
        }
    }

    int subscriberCount() {
        return subscribers.size();
    }

    private final class Subscriber {
        private final UUID id;
        private final SseEmitter emitter;
        private final List<AgentRunCommittedEvent> buffer = new ArrayList<>();
        private AgentRunEventBus.Subscription subscription;
        private AgentRunSnapshot lastRun;
        private long lastVersion = -1;
        private boolean initialized;
        private boolean closed;

        private Subscriber(UUID id, SseEmitter emitter) {
            this.id = id;
            this.emitter = emitter;
        }

        synchronized void bind(AgentRunEventBus.Subscription value) {
            subscription = value;
        }

        synchronized void initialize(AgentRunSnapshot snapshot) {
            if (closed) return;
            send("snapshot", snapshot.stateVersion(), new SnapshotEventDto(
                    snapshot.id(), snapshot.stateVersion(), clock.instant(), mapper.detail(snapshot)));
            lastRun = snapshot;
            lastVersion = snapshot.stateVersion();
            initialized = true;
            buffer.stream()
                    .filter(event -> event.run().stateVersion() > lastVersion)
                    .sorted(Comparator.comparingLong(event -> event.run().stateVersion()))
                    .forEach(this::sendEvent);
            buffer.clear();
            if (snapshot.status().isTerminal()) {
                closeAfterSend();
            }
        }

        synchronized void onEvent(AgentRunCommittedEvent event) {
            if (closed) return;
            if (!initialized) {
                buffer.add(event);
                return;
            }
            if (event.run().stateVersion() <= lastVersion) {
                return;
            }
            sendEvent(event);
        }

        synchronized void heartbeat(Instant now) {
            if (closed || !initialized || lastRun == null || lastRun.status().isTerminal()) return;
            send("heartbeat", lastVersion, new HeartbeatEventDto(
                    lastRun.id(), lastVersion, now, now, lastRun.status()));
        }

        private void sendEvent(AgentRunCommittedEvent event) {
            AgentRunSnapshot run = event.run();
            Object payload = switch (event.type()) {
                case SNAPSHOT -> new SnapshotEventDto(
                        run.id(), run.stateVersion(), event.occurredAt(), mapper.detail(run));
                case PROGRESS -> new ProgressEventDto(
                        run.id(), run.stateVersion(), event.occurredAt(), run.status(),
                        run.currentStep(), run.progressPercent(), run.actualCostUsd());
                case STEP -> new StepEventDto(
                        run.id(), run.stateVersion(), event.occurredAt(), mapper.step(event.step()));
                case WAITING_USER -> new WaitingUserEventDto(
                        run.id(), run.stateVersion(), event.occurredAt(),
                        mapper.action(run.requiredUserAction()));
                case HEARTBEAT -> new HeartbeatEventDto(
                        run.id(), run.stateVersion(), event.occurredAt(),
                        event.occurredAt(), run.status());
                case TERMINAL -> new TerminalEventDto(
                        run.id(), run.stateVersion(), event.occurredAt(), run.status(),
                        run.completedAt(), run.actualCostUsd(), run.retryable(),
                        mapper.error(run.safeError()), run.resourceType(), run.resourceId());
            };
            send(event.type().name().toLowerCase(java.util.Locale.ROOT), run.stateVersion(), payload);
            lastRun = run;
            lastVersion = run.stateVersion();
            if (run.status().isTerminal()) {
                closeAfterSend();
            }
        }

        private void send(String eventName, long version, Object payload) {
            try {
                emitter.send(SseEmitter.event()
                        .id(Long.toString(version))
                        .name(eventName)
                        .data(payload));
            } catch (IOException | IllegalStateException exception) {
                close();
            }
        }

        private void closeAfterSend() {
            try {
                emitter.complete();
            } finally {
                close();
            }
        }

        synchronized void close() {
            if (closed) return;
            closed = true;
            subscribers.remove(id, this);
            if (subscription != null) {
                subscription.close();
            }
            buffer.clear();
        }
    }
}
