package com.viaversion.aas.util;

import com.viaversion.viaversion.api.protocol.packet.State;

public enum IntendedState {
    INVALID(null),
    STATUS(State.STATUS),
    LOGIN(State.LOGIN),
    TRANSFER(State.LOGIN);

    private final State state;

    IntendedState(State state) {
        this.state = state;
    }

    public State getState() {
        return state;
    }
}
