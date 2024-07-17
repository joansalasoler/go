package com.joansala.game.go.scorers;

/*
 * Samurai framework.
 * Copyright (C) 2024 Joan Sala Soler <contact@joansala.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation,  either version 3 of the License,  or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not,  see <http://www.gnu.org/licenses/>.
 */

import com.joansala.engine.Scorer;
import com.joansala.game.go.GoGame;
import com.joansala.game.go.attacks.Point;
import com.joansala.util.bits.Bitset;

import static com.joansala.game.go.Go.*;


/**
 *
 */
public final class AreaScorer implements Scorer<GoGame> {

    /**
     * Compute the current score of the players.
     *
     * This includes, for each player, the number of stones of that color
     * plus the number of intersections on an empty area that is surrounded
     * only by stones of that single color.
     *
     * @return          Accumulated scores for each player
     */
    public final int evaluate(GoGame game) {
        Bitset areas = new Bitset(BITSET_SIZE);
        int black = game.state(BLACK).count();
        int white = game.state(WHITE).count();
        int empty = 0;

        for (int point = 0; point < BOARD_SIZE; point++) {
            if (!areas.contains(point) && game.isEmptyPoint(point)) {
                int[] counts = new int[2];
                areas(game, areas, counts, point);
                int count = areas.count() - empty;
                empty += count;

                if (counts[BLACK] == 0) {
                    white += count;
                } else if (counts[WHITE] == 0) {
                    black += count;
                }
            }
        }

        return STONE_SCORE * (black - white);
    }


    /**
     * Fills a bitboard with a chain of empty intersections starting
     * at the given point and counts the neighbors of each color.
     * Notice that a neighbor stone may be counted multiple times.
     *
     * @param areas     Bitset to fill
     * @param counts    Array where counts are added
     * @param point     Empty start point
     */
    private void areas(GoGame game, Bitset areas, int[] counts, int point) {
        areas.insert(point);

        final Bitset black = game.state(BLACK);
        final Bitset white = game.state(WHITE);

        for (int neighbor : Point.attacks(point)) {
            if (areas.contains(neighbor) == false) {
                if (black.contains(neighbor)) {
                    counts[BLACK]++;
                } else if (white.contains(neighbor)) {
                    counts[WHITE]++;
                } else {
                    areas(game, areas, counts, neighbor);
                }
            }
        }
    }
}
