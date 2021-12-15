package com.joansala.test.game.go;

import org.junit.jupiter.api.*;
import com.joansala.engine.Game;
import com.joansala.test.engine.GameContract;
import com.joansala.game.go.GoGame;


@DisplayName("Go game")
public class GoGameTest implements GameContract {

    /**
     * {@inheritDoc}
     */
    @Override
    public Game newInstance() {
        return new GoGame();
    }
}
