package com.joansala.game.go.uci;

/*
 * Copyright (C) 2014-2024 Joan Sala Soler <contact@joansala.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.joansala.uci.UCIService;
import com.joansala.uci.util.SpinOption;
import com.joansala.game.go.GoGame;
import static com.joansala.game.go.Go.*;


/**
 * Compensation points for the white player.
 */
public class KomiOption extends SpinOption {

    /**
     * Creates a new option instance.
     */
    public KomiOption() {
        super(DEFAULT_KOMI, 0, INFINITY_SCORE - STONE_SCORE);
    }


    /**
     * {@inheritDoc}
     */
    public void initialize(UCIService service) {
        GoGame game = (GoGame) service.getGame().cast();
        game.setKomiScore(DEFAULT_KOMI);
    }


    /**
     * {@inheritDoc}
     */
    public void handle(UCIService service, int value) {
        GoGame game = (GoGame) service.getGame().cast();
        service.debug("Komi score is now " + value + " cp");
        game.setKomiScore(value);
    }
}
