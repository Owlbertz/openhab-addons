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

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ModeOnWake}
 *
 * @author Nick Waterton - Initial contribution
 */
@NonNullByDefault
public class ModeOnWakeTest {

    private final AtomicLong now = new AtomicLong(1000);
    private final ModeOnWake modeOnWake = new ModeOnWake(now::get);

    @Test
    public void requestsCommandedModeOnce() {
        modeOnWake.set(true);
        assertTrue(modeOnWake.take(false));
        assertFalse(modeOnWake.take(false));
    }

    @Test
    public void nothingToRequestInCommandedMode() {
        modeOnWake.set(false);
        assertFalse(modeOnWake.take(false));
    }

    @Test
    public void offBeforeFirstReportCancels() {
        modeOnWake.set(true);
        modeOnWake.clear();
        assertFalse(modeOnWake.take(false));
    }

    @Test
    public void expiresAfterAMinute() {
        modeOnWake.set(true);
        now.addAndGet(60000);
        assertFalse(modeOnWake.take(false));
    }

    @Test
    public void wakingUntilReportedModeIsApplied() {
        modeOnWake.set(true);
        assertTrue(modeOnWake.retarget(true));
        modeOnWake.take(true);
        assertFalse(modeOnWake.retarget(true));
    }

    @Test
    public void commandAfterAppliedReportDoesNotRetarget() {
        modeOnWake.set(false);
        assertFalse(modeOnWake.take(false));
        assertFalse(modeOnWake.retarget(true));
        assertFalse(modeOnWake.take(false));
    }

    @Test
    public void wokeEndsWakingAndKeepsRequestedMode() {
        modeOnWake.set(true);
        modeOnWake.woke();
        assertFalse(modeOnWake.retarget(false));
        assertTrue(modeOnWake.take(false));
    }

    @Test
    public void retargetKeepsDeadlines() {
        modeOnWake.set(false);
        now.addAndGet(10000);
        assertTrue(modeOnWake.retarget(true));
        now.addAndGet(5000);
        assertFalse(modeOnWake.retarget(false));
        now.addAndGet(45000);
        assertFalse(modeOnWake.take(false));
    }

    @Test
    public void retargetRequestsOtherMode() {
        modeOnWake.set(false);
        assertTrue(modeOnWake.retarget(true));
        assertTrue(modeOnWake.take(false));
    }

    @Test
    public void wakingExpires() {
        modeOnWake.set(true);
        now.addAndGet(15000);
        assertFalse(modeOnWake.retarget(true));
    }

    @Test
    public void offStopsWaking() {
        modeOnWake.set(true);
        modeOnWake.clear();
        assertFalse(modeOnWake.retarget(true));
    }

    @Test
    public void notWakingWithoutCommand() {
        assertFalse(modeOnWake.retarget(true));
        modeOnWake.woke();
        assertFalse(modeOnWake.retarget(true));
    }

    @Test
    public void nothingPendingWithoutCommand() {
        assertFalse(modeOnWake.take(true));
        assertFalse(modeOnWake.take(false));
    }
}
