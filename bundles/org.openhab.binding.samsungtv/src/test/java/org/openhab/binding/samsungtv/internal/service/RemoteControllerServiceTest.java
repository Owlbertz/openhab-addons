/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.samsungtv.internal.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.samsungtv.internal.SamsungTvBindingConstants.ART_MODE;
import static org.openhab.binding.samsungtv.internal.SamsungTvBindingConstants.POWER;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.samsungtv.internal.config.SamsungTvConfiguration;
import org.openhab.binding.samsungtv.internal.handler.SamsungTvHandler;
import org.openhab.core.io.net.http.WebSocketFactory;
import org.openhab.core.library.types.OnOffType;

/**
 * Tests for {@link RemoteControllerService}
 *
 * @author Nick Waterton - Initial contribution
 */
@NonNullByDefault
public class RemoteControllerServiceTest {

    private final SamsungTvHandler handler = mock(SamsungTvHandler.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final List<Runnable> scheduled = new ArrayList<>();
    private final ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);

    @BeforeEach
    public void setUp() {
        handler.configuration = new SamsungTvConfiguration();
        WebSocketFactory webSocketFactory = mock(WebSocketFactory.class);
        when(webSocketFactory.createWebSocketClient(anyString(), any())).thenReturn(mock(WebSocketClient.class));
        when(handler.getWebSocketFactory()).thenReturn(webSocketFactory);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> queued.add(invocation.getArgument(0))).when(scheduler).execute(any());
        doAnswer(invocation -> {
            scheduled.add(invocation.getArgument(0));
            return scheduledFuture;
        }).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        when(handler.getScheduler()).thenReturn(scheduler);
        when(handler.fetchPowerState()).thenReturn("on");
    }

    @Test
    public void artModeWakeIsNotDeferredWithoutConnection() throws Exception {
        RemoteControllerService service = new RemoteControllerService("192.168.1.10", 8002, false, handler);

        assertFalse(service.handleCommand(ART_MODE, OnOffType.ON));

        verify(handler, never()).handleCommand(eq(ART_MODE), eq(OnOffType.ON), anyInt());
    }

    @Test
    public void powerWakeIsNotDeferredWithoutConnection() throws Exception {
        RemoteControllerService service = new RemoteControllerService("192.168.1.10", 8002, false, handler);

        assertFalse(service.handleCommand(POWER, OnOffType.ON));

        verify(handler, never()).handleCommand(eq(POWER), eq(OnOffType.ON), anyInt());
    }

    @Test
    public void powerUpdateQueuedBeforeStopIsDropped() throws Exception {
        RemoteControllerService service = new RemoteControllerService("192.168.1.10", 8002, false, handler);
        service.powerUpdatedAsync(true, false);
        service.stop();
        assertFalse(queued.isEmpty());
        queued.forEach(Runnable::run);

        verify(handler, never()).fetchPowerState();
        verify(handler, never()).valueReceived(anyString(), any());
    }

    @Test
    public void powerUpdateIsApplied() throws Exception {
        RemoteControllerService service = new RemoteControllerService("192.168.1.10", 8002, false, handler);
        service.powerUpdatedAsync(true, false);
        queued.forEach(Runnable::run);

        verify(handler).fetchPowerState();
        verify(handler, atLeastOnce()).valueReceived(anyString(), any());
    }

    @Test
    public void artModeStatusIsRequestedAtMostThreeTimes() throws Exception {
        when(handler.getArtModeSupported()).thenReturn(true);
        RemoteControllerService service = new RemoteControllerService("192.168.1.10", 8002, false, handler);
        service.refreshArtModeStatus();
        runScheduled();

        verify(handler.getScheduler(), times(2)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    public void artModeStatusIsNotRepeatedOnceModeIsKnown() throws Exception {
        when(handler.getArtModeSupported()).thenReturn(true);
        RemoteControllerService service = new RemoteControllerService("192.168.1.10", 8002, false, handler);
        service.refreshArtModeStatus();
        service.artMode = true;
        runScheduled();

        verify(handler.getScheduler(), times(1)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    public void stopCancelsArtModeStatusRetry() throws Exception {
        when(handler.getArtModeSupported()).thenReturn(true);
        RemoteControllerService service = new RemoteControllerService("192.168.1.10", 8002, false, handler);
        service.refreshArtModeStatus();
        service.stop();

        verify(scheduledFuture).cancel(false);
        runScheduled();
        verify(handler.getScheduler(), times(1)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    public void newerRefreshReplacesArtModeStatusRetry() throws Exception {
        when(handler.getArtModeSupported()).thenReturn(true);
        RemoteControllerService service = new RemoteControllerService("192.168.1.10", 8002, false, handler);
        service.refreshArtModeStatus();
        service.refreshArtModeStatus();

        verify(scheduledFuture).cancel(false);
        // the retry of the first refresh is dropped, only the second refresh goes on to its third request
        runScheduled();
        verify(handler.getScheduler(), times(3)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    public void artModeStatusIsNotRequestedWithoutArtMode() throws Exception {
        RemoteControllerService service = new RemoteControllerService("192.168.1.10", 8002, false, handler);
        service.refreshArtModeStatus();

        verify(handler.getScheduler(), never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    private void runScheduled() {
        while (!scheduled.isEmpty()) {
            scheduled.remove(0).run();
        }
    }
}
