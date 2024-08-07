package com.joansala.game.go;

/*
 * Aalina engine.
 * Copyright (C) 2021-2024 Joan Sala Soler <contact@joansala.com>
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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import com.joansala.engine.Board;
import com.joansala.engine.Scorer;
import com.joansala.engine.base.BaseGame;
import com.joansala.util.hash.ZobristHash;
import com.joansala.util.bits.Bitset;
import com.joansala.game.go.Go.Player;
import com.joansala.game.go.attacks.Point;
import com.joansala.game.go.scorers.AreaScorer;

import static com.joansala.game.go.Go.*;


/**
 * Represents a Go game between two players.
 */
public class GoGame extends BaseGame {

    /** Maximum number of plies this object can store */
    public static final int MAX_CAPACITY = Integer.MAX_VALUE / (BITSET_SIZE << 1);

    /** Capacity increases at least this value each time */
    private static final int CAPACITY_INCREMENT = 128;

    /** Heuristic evaluation function */
    private static final Scorer<GoGame> scorer = scoreFunction();

    /** Hash code generator */
    private static final ZobristHash hasher = hashFunction();

    /** Start position and turn */
    private GoBoard board;

    /** Player to move */
    private Player player;

    /** Player to move opponent */
    private Player rival;

    /** Move generation cursors */
    private int[] cursors;

    /** Ko point history */
    private int[] kopoints;

    /** Hash code history */
    private long[] hashes;

    /** Position bitboards history */
    private long[] states;

    /** Current position bitboards */
    private Bitset[] state;

    /** Current move generation cursor */
    private int cursor;

    /** Current illegal ko move */
    private int kopoint;

    /** Compensation score for white */
    private int komi = DEFAULT_KOMI;


    /**
     * Instantiate a new game on the start state.
     */
    public GoGame() {
        this(DEFAULT_CAPACITY);
    }


    /**
     * Instantiate a new game on the start state.
     *
     * @param capacity      Initial capacity
     */
    public GoGame(int capacity) {
        super(capacity);
        cursors = new int[capacity];
        kopoints = new int[capacity];
        hashes = new long[capacity];
        states = new long[capacity * BITSET_SIZE << 1];
        setBoard(new GoBoard());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int turn() {
        return player.turn;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Board getBoard() {
        return board;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setBoard(Board board) {
        setBoard((GoBoard) board);
    }


    /**
     * {@see #setBoard(Board)}
     */
    public void setBoard(GoBoard board) {
        this.index = -1;
        this.board = board;
        this.move = NULL_MOVE;
        this.kopoint = board.kopoint();
        this.state = board.position();

        setTurn(board.turn());
        hash = computeHash();
        resetCursor();
    }


    /**
     * Sets the current player to move.
     *
     * @param turn      {@code SOUTH} or {@code NORTH}
     */
    private void setTurn(int turn) {
        if (turn == SOUTH) {
            player = Player.SOUTH;
            rival = Player.NORTH;
        } else {
            player = Player.NORTH;
            rival = Player.SOUTH;
        }
    }


    /**
     * Toggles the player to move.
     */
    private void switchTurn() {
        Player player = this.player;
        this.player = rival;
        this.rival = player;
    }


    /**
     * Sets the handicap value for black.
     */
    public void setKomiScore(int komi) {
        this.komi = komi;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public GoBoard toBoard() {
        return new GoBoard(state, turn);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLegal(int move) {
        if (isForfeit(move)) {
            return true;
        }

        if (isKoPoint(move) || !isEmptyPoint(move)) {
            return false;
        }

        return !isSuicide(player.color, move);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasEnded() {
        if (index < 0) {
            return false;
        }

        if (isForfeit(move) && isForfeit(moves[index])) {
            return true;
        }

        return isRepetition();
    }


    /**
     * Check if it is a forfeit move identifier.
     */
    public boolean isForfeit(int move) {
        return move == FORFEIT_MOVE;
    }


    /**
     * Check if a move targets the current Ko point.
     */
    public boolean isKoPoint(int move) {
        return move == kopoint;
    }


    /**
     * Check if an intersection does not contain any stones.
     */
    public boolean isEmptyPoint(int index) {
       return !state[BLACK].contains(index) &&
              !state[WHITE].contains(index);
    }


    /**
     * Check if a stone would be captured immediately if placed on a point.
     * That is, if the move would not capture any rival stones and the
     * chain of the placed stone would have no liberties.
     *
     * @param color         Stone color
     * @param point         Intersection point
     */
    private boolean isSuicide(int color, int point) {
        Chain chain = chain(color, point);

        if (chain.liberties.count() != 0) {
            return false;
        }

        for (int neighbor: Point.attacks(point)) {
            if (state[1 ^ color].contains(neighbor)) {
                if (chain(1 ^ color, neighbor).isInAtari()) {
                    return false;
                }
            }
        }

        return true;
    }


    /**
     * Checks if the same state occurred before.
     *
     * @return      If a repetition occurred
     */
    private boolean isRepetition() {
        for (int n = index - 1; n >= 0; n--) {
            if (moves[n] != FORFEIT_MOVE) {
                if (hashes[n] == this.hash) {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Obtain the current bitboard on the given index.
     *
     * @return      Bitboard
     */
    public final Bitset state(int index) {
        return state[index];
    }


    /**
     * Current game state reference.
     *
     * @return      A game state
     */
    protected Bitset[] state() {
        return state;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int contempt() {
        return CONTEMPT_SCORE;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int infinity() {
        return INFINITY_SCORE;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int winner() {
        final int score = outcome();

        if (score == INFINITY_SCORE) {
            return SOUTH;
        }

        if (score == -INFINITY_SCORE) {
            return NORTH;
        }

        return DRAW;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int toCentiPawns(int score) {
        return score;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int outcome() {
        int score = scorer.evaluate(this) - komi;
        if (score < DRAW_SCORE) return -INFINITY_SCORE;
        if (score > DRAW_SCORE) return INFINITY_SCORE;
        return DRAW_SCORE;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int score() {
        return scorer.evaluate(this) - komi;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int getCursor() {
        return cursor;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setCursor(int cursor) {
        this.cursor = cursor;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void resetCursor() {
        this.cursor = NULL_MOVE;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void makeMove(int move) {
        pushState();
        movePieces(move);
        switchTurn();
        this.move = move;
        resetCursor();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void unmakeMove() {
        popState(index);
        switchTurn();
        index--;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void unmakeMoves(int length) {
        if (length > 0) {
            index -= length;
            setTurn((length & 1) == 0 ? turn() : -turn());
            popState(1 + index);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int nextMove() {
        while (cursor < FORFEIT_MOVE) {
            if (isLegal(++cursor)) {
                return cursor;
            }
        }

        return NULL_MOVE;
    }


    /**
     * Performs a move on the current position.
     *
     * @param move      Move to perform
     */
    private void movePieces(int move) {
        // Toggle the hash sign

        hash ^= rival.sign;
        hash ^= player.sign;

        // Player forfeits the turn

        if (move == FORFEIT_MOVE) {
            return;
        }

        // Remove captures and place a new stone

        int captures = 0;

        for (int point : Point.attacks(move)) {
            if (state[rival.color].contains(point)) {
                Chain chain = chain(rival.color, point);

                if (chain.isInAtari()) {
                    chain.stones.forEach(i -> capture(i));
                    this.kopoint = point;
                    captures++;
                }
            }
        }

        place(move);

        // Clear ko point

        if (captures != 1) {
            this.kopoint = NULL_MOVE;
        }
    }


    /**
     * Adds a piece of the current player to a point.
     */
    private void place(int point) {
        state[player.color].insert(point);
        hash = hasher.insert(hash, point, player.color);
    }


    /**
     * Removes a piece of the rival player from a point.
     */
    private void capture(int point) {
        state[rival.color].toggle(point);
        hash = hasher.remove(hash, point, rival.color);
    }


    /**
     * Build a chain of stones starting at the given point. This method
     * assumes there is a stone of the given color on the point.
     *
     * @param color         Color of stones
     * @param point         Start point
     */
    private Chain chain(int color, int point) {
        final Chain chain = new Chain();
        chain(chain, color, point);
        return chain;
    }


    /**
     * Recursively fills a chain starting from a point.
     *
     * @param color         Color of stones
     * @param point         Start point
     */
    private void chain(Chain chain, int color, int point) {
        chain.stones.insert(point);

        for (int neighbor : Point.attacks(point)) {
            if (chain.stones.contains(neighbor) == false) {
                if (state[color].contains(neighbor)) {
                    chain(chain, color, neighbor);
                } else if (!state[1 ^ color].contains(neighbor)) {
                    chain.liberties.insert(neighbor);
                }
            }
        }
    }


    /**
     * Store game state on the history.
     */
    private void pushState() {
        index++;
        moves[index] = move;
        hashes[index] = hash;
        cursors[index] = cursor;
        kopoints[index] = kopoint;

        final int i = index * BITSET_SIZE << 1;
        state[WHITE].copyTo(states, i + BITSET_SIZE);
        state[BLACK].copyTo(states, i);
    }


    /**
     * Retrieve the current game state from the history.
     */
    private void popState(int index) {
        final int i = index * BITSET_SIZE << 1;
        state[WHITE].copyFrom(states, i + BITSET_SIZE);
        state[BLACK].copyFrom(states, i);

        kopoint = kopoints[index];
        cursor = cursors[index];
        hash = hashes[index];
        move = moves[index];
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected long computeHash() {
        AtomicLong hash = new AtomicLong(player.sign);

        for (int piece = 0; piece < PIECE_COUNT; piece++) {
            final int stone = piece;

            state[piece].forEach(index -> {
                final long value = hash.longValue();
                hash.set(hasher.insert(value, index, stone));
            });
        }

        return hash.longValue();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void ensureCapacity(int size) {
        if (size > this.capacity) {
            size = Math.max(size, capacity + CAPACITY_INCREMENT);
            size = Math.min(MAX_CAPACITY, size);

            states = Arrays.copyOf(states, size * (BITSET_SIZE << 1));
            kopoints = Arrays.copyOf(kopoints, size);
            cursors = Arrays.copyOf(cursors, size);
            hashes = Arrays.copyOf(hashes, size);
            moves = Arrays.copyOf(moves, size);
            capacity = size;

            System.gc();
        }
    }


    /**
     * Initialize the heuristic evaluation function.
     */
    private static Scorer<GoGame> scoreFunction() {
        return new AreaScorer();
    }


    /**
     * Initialize the hash code generator.
     */
    private static ZobristHash hashFunction() {
        return new ZobristHash(RANDOM_SEED, PIECE_COUNT, BOARD_SIZE);
    }


    /**
     * Stores a chain of stones and its liberties.
     */
    protected class Chain {
        public Bitset stones = new Bitset(BITSET_SIZE);
        public Bitset liberties = new Bitset(BITSET_SIZE);

        /**
         * Check if the chain has only one liberty.
         */
        public boolean isInAtari() {
            return 1 == liberties.count();
        }
    }
}
