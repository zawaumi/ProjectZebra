package com.zawa.client.ai.research;

import com.zawa.client.ai.AbstractClientAi;

import java.util.SplittableRandom;

public class ClientAlternativeMCTSAI extends AbstractClientAi {

    private static final int TIME_LIMIT_MS = 2400;
    private static final double EXPLORATION = 1.41421356;
    private static final long CORNERS = 0x8100000000000081L;
    private static final long DANGERS = 0x42c300000000c342L;

    private SplittableRandom random = new SplittableRandom();

    private static class Node {
        long pBoard;
        long oBoard;
        long unexpanded;
        int move;
        int visits;
        double score;
        Node parent;
        Node[] children;
        int childCount;
        boolean isRootTurn;

        Node(long p, long o, int m, Node parent, boolean isRootTurn) {
            this.pBoard = p;
            this.oBoard = o;
            this.move = m;
            this.parent = parent;
            this.isRootTurn = isRootTurn;
            long moves = getLegalMoves(p, o);
            if (moves == 0L && getLegalMoves(o, p) != 0L) {
                this.pBoard = o;
                this.oBoard = p;
                this.isRootTurn = !isRootTurn;
                moves = getLegalMoves(this.pBoard, this.oBoard);
            }
            this.unexpanded = moves;
            this.children = new Node[Long.bitCount(moves)];
            this.childCount = 0;
        }
    }

    public ClientAlternativeMCTSAI(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
        this.ai_name = "AlternativeMCTSAI";
    }

    public ClientAlternativeMCTSAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "AlternativeMCTSAI";
    }

    @Override
    public Integer[] estimateNextPut(Integer[][] othello_array, Integer myturn) {
        long myBoard = 0L;
        long oppBoard = 0L;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (othello_array[r][c] != null) {
                    long bit = 1L << (r * 8 + c);
                    if (othello_array[r][c].equals(myturn)) {
                        myBoard |= bit;
                    } else if (othello_array[r][c].equals(-myturn)) {
                        oppBoard |= bit;
                    }
                }
            }
        }

        long legalMoves = getLegalMoves(myBoard, oppBoard);
        if (legalMoves == 0L) {
            return null;
        }

        if (Long.bitCount(legalMoves) == 1) {
            int move = Long.numberOfTrailingZeros(legalMoves);
            return new Integer[]{move / 8, move % 8};
        }

        long endTime = System.currentTimeMillis() + TIME_LIMIT_MS;
        Node root = new Node(myBoard, oppBoard, -1, null, true);

        int iterations = 0;
        while (true) {
            if ((iterations++ & 2047) == 0 && System.currentTimeMillis() >= endTime) {
                break;
            }
            Node node = select(root);
            Node expanded = expand(node);
            double result = simulate(expanded);
            backpropagate(expanded, result);
        }

        int bestMove = -1;
        int maxVisits = -1;
        for (int i = 0; i < root.childCount; i++) {
            if (root.children[i].visits > maxVisits) {
                maxVisits = root.children[i].visits;
                bestMove = root.children[i].move;
            }
        }

        if (bestMove == -1) {
            bestMove = Long.numberOfTrailingZeros(legalMoves);
        }

        return new Integer[]{bestMove / 8, bestMove % 8};
    }

    private Node select(Node node) {
        while (node.unexpanded == 0L && node.childCount > 0) {
            Node bestChild = null;
            double bestValue = -1.0;
            for (int i = 0; i < node.childCount; i++) {
                Node child = node.children[i];
                double uct = (child.score / child.visits) + EXPLORATION * Math.sqrt(Math.log(node.visits) / child.visits);
                if (uct > bestValue) {
                    bestValue = uct;
                    bestChild = child;
                }
            }
            node = bestChild;
        }
        return node;
    }

    private Node expand(Node node) {
        if (node.unexpanded == 0L) return node;
        int m = Long.numberOfTrailingZeros(node.unexpanded);
        node.unexpanded &= node.unexpanded - 1L;
        long flip = getFlip(node.pBoard, node.oBoard, m);
        Node child = new Node(node.oBoard ^ flip, node.pBoard | (1L << m) | flip, m, node, !node.isRootTurn);
        node.children[node.childCount++] = child;
        return child;
    }

    private double simulate(Node node) {
        long p = node.pBoard;
        long o = node.oBoard;
        boolean rootTurn = node.isRootTurn;
        while (true) {
            long moves = getLegalMoves(p, o);
            if (moves == 0L) {
                long oppMoves = getLegalMoves(o, p);
                if (oppMoves == 0L) break;
                long t = p;
                p = o;
                o = t;
                rootTurn = !rootTurn;
                moves = oppMoves;
            }
            long priority = moves & CORNERS;
            if (priority == 0L) {
                priority = moves & ~DANGERS;
                if (priority == 0L) priority = moves;
            }
            int count = Long.bitCount(priority);
            int r = random.nextInt(count);
            long temp = priority;
            for (int i = 0; i < r; i++) temp &= temp - 1L;
            int m = Long.numberOfTrailingZeros(temp);
            long flip = getFlip(p, o, m);
            long np = o ^ flip;
            long no = p | (1L << m) | flip;
            p = np;
            o = no;
            rootTurn = !rootTurn;
        }
        int rootCount = rootTurn ? Long.bitCount(p) : Long.bitCount(o);
        int oppCount = rootTurn ? Long.bitCount(o) : Long.bitCount(p);
        if (rootCount > oppCount) return 1.0;
        if (rootCount < oppCount) return 0.0;
        return 0.5;
    }

    private void backpropagate(Node node, double result) {
        while (node != null) {
            node.visits++;
            if (node.isRootTurn) {
                node.score += (1.0 - result);
            } else {
                node.score += result;
            }
            node = node.parent;
        }
    }

    private static long getLegalMoves(long my, long opp) {
        long empty = ~(my | opp);
        long legal = 0L;
        long w = opp & 0x7E7E7E7E7E7E7E7EL;
        long t = w & (my >>> 1);
        t |= w & (t >>> 1); t |= w & (t >>> 1); t |= w & (t >>> 1); t |= w & (t >>> 1); t |= w & (t >>> 1);
        legal |= empty & (t >>> 1);
        t = w & (my << 1);
        t |= w & (t << 1); t |= w & (t << 1); t |= w & (t << 1); t |= w & (t << 1); t |= w & (t << 1);
        legal |= empty & (t << 1);
        w = opp & 0x00FFFFFFFFFFFF00L;
        t = w & (my >>> 8);
        t |= w & (t >>> 8); t |= w & (t >>> 8); t |= w & (t >>> 8); t |= w & (t >>> 8); t |= w & (t >>> 8);
        legal |= empty & (t >>> 8);
        t = w & (my << 8);
        t |= w & (t << 8); t |= w & (t << 8); t |= w & (t << 8); t |= w & (t << 8); t |= w & (t << 8);
        legal |= empty & (t << 8);
        w = opp & 0x007E7E7E7E7E7E00L;
        t = w & (my >>> 7);
        t |= w & (t >>> 7); t |= w & (t >>> 7); t |= w & (t >>> 7); t |= w & (t >>> 7); t |= w & (t >>> 7);
        legal |= empty & (t >>> 7);
        t = w & (my << 7);
        t |= w & (t << 7); t |= w & (t << 7); t |= w & (t << 7); t |= w & (t << 7); t |= w & (t << 7);
        legal |= empty & (t << 7);
        t = w & (my >>> 9);
        t |= w & (t >>> 9); t |= w & (t >>> 9); t |= w & (t >>> 9); t |= w & (t >>> 9); t |= w & (t >>> 9);
        legal |= empty & (t >>> 9);
        t = w & (my << 9);
        t |= w & (t << 9); t |= w & (t << 9); t |= w & (t << 9); t |= w & (t << 9); t |= w & (t << 9);
        legal |= empty & (t << 9);
        return legal;
    }

    private static long getFlip(long my, long opp, int move) {
        long flip = 0L;
        long p = 1L << move;
        long mask = opp & 0x7E7E7E7E7E7E7E7EL;
        long out = mask & (p >>> 1);
        out |= mask & (out >>> 1); out |= mask & (out >>> 1); out |= mask & (out >>> 1); out |= mask & (out >>> 1); out |= mask & (out >>> 1);
        if ((my & (out >>> 1)) != 0) flip |= out;
        out = mask & (p << 1);
        out |= mask & (out << 1); out |= mask & (out << 1); out |= mask & (out << 1); out |= mask & (out << 1); out |= mask & (out << 1);
        if ((my & (out << 1)) != 0) flip |= out;
        mask = opp & 0x00FFFFFFFFFFFF00L;
        out = mask & (p >>> 8);
        out |= mask & (out >>> 8); out |= mask & (out >>> 8); out |= mask & (out >>> 8); out |= mask & (out >>> 8); out |= mask & (out >>> 8);
        if ((my & (out >>> 8)) != 0) flip |= out;
        out = mask & (p << 8);
        out |= mask & (out << 8); out |= mask & (out << 8); out |= mask & (out << 8); out |= mask & (out << 8); out |= mask & (out << 8);
        if ((my & (out << 8)) != 0) flip |= out;
        mask = opp & 0x007E7E7E7E7E7E00L;
        out = mask & (p >>> 9);
        out |= mask & (out >>> 9); out |= mask & (out >>> 9); out |= mask & (out >>> 9); out |= mask & (out >>> 9); out |= mask & (out >>> 9);
        if ((my & (out >>> 9)) != 0) flip |= out;
        out = mask & (p << 9);
        out |= mask & (out << 9); out |= mask & (out << 9); out |= mask & (out << 9); out |= mask & (out << 9); out |= mask & (out << 9);
        if ((my & (out << 9)) != 0) flip |= out;
        out = mask & (p >>> 7);
        out |= mask & (out >>> 7); out |= mask & (out >>> 7); out |= mask & (out >>> 7); out |= mask & (out >>> 7); out |= mask & (out >>> 7);
        if ((my & (out >>> 7)) != 0) flip |= out;
        out = mask & (p << 7);
        out |= mask & (out << 7); out |= mask & (out << 7); out |= mask & (out << 7); out |= mask & (out << 7); out |= mask & (out << 7);
        if ((my & (out << 7)) != 0) flip |= out;
        return flip;
    }
}