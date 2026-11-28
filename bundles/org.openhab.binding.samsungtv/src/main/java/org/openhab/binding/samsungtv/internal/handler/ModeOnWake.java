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
package org.openhab.binding.samsungtv.internal.handler;

import java.util.function.LongSupplier;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link ModeOnWake} remembers the mode a power or artMode ON command asked for while the TV wakes. A Frame TV
 * wakes in the mode it was switched off from, so the commanded mode is requested if the first art mode report within
 * the next minute differs. Until the TV's reported mode is applied, or for at most 15 seconds, it counts as waking,
 * and a further ON command only changes the mode to request.
 *
 * @author Nick Waterton - Initial contribution
 */
@NonNullByDefault
class ModeOnWake {

    private static final long WINDOW_MS = 60000;
    private static final long WAKING_MS = 15000;

    private final LongSupplier clock;
    private long until = 0;
    private long wakingUntil = 0;
    private boolean artMode;

    ModeOnWake() {
        this(System::currentTimeMillis);
    }

    ModeOnWake(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * @param artMode true for an artMode ON command, false for a power ON command
     */
    synchronized void set(boolean artMode) {
        this.artMode = artMode;
        long now = clock.getAsLong();
        until = now + WINDOW_MS;
        wakingUntil = now + WAKING_MS;
    }

    synchronized void clear() {
        until = 0;
        wakingUntil = 0;
    }

    /**
     * A later ON command while the TV wakes changes the mode to request, keeping the deadlines. The check and the
     * change are one step, so a command either changes a mode that {@link #take(boolean)} has yet to use, or is told
     * that the wake is over.
     *
     * @param artMode true for an artMode ON command, false for a power ON command
     * @return true if an ON command is waking the TV and its reported mode has not been applied yet
     */
    synchronized boolean retarget(boolean artMode) {
        if (clock.getAsLong() < wakingUntil) {
            this.artMode = artMode;
            return true;
        }
        return false;
    }

    /**
     * Called once a power state that did not come from an art mode report has been applied. The TV no longer counts
     * as waking, the commanded mode is kept for the first art mode report.
     */
    synchronized void woke() {
        wakingUntil = 0;
    }

    /**
     * Called once the art mode the TV reports has been applied. Only the first report after a wake counts, and it
     * ends the wake.
     *
     * @param reportedArtMode the art mode the TV reports
     * @return true if a wake is pending and the TV is not in the commanded mode
     */
    synchronized boolean take(boolean reportedArtMode) {
        wakingUntil = 0;
        if (clock.getAsLong() < until) {
            until = 0;
            return reportedArtMode != artMode;
        }
        return false;
    }
}
