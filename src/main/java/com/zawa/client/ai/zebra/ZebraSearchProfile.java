package com.zawa.client.ai.zebra;

public record ZebraSearchProfile(int exactAttemptEmpties, int probCutMinimumDepth,
                                 int probCutBaseMargin, int probCutDepthMargin,
                                 boolean lateMoveReduction, boolean expertOpeningPolicy,
                                 int transpositionWays, int endgameTailEmpties) {
    public static ZebraSearchProfile tcl() {
        return new ZebraSearchProfile(18, 7, 700, 70, false, false, 1, 0);
    }

    public static ZebraSearchProfile vanguardControl() {
        return new ZebraSearchProfile(22, 7, 620, 60, true, true, 1, 0);
    }

    public static ZebraSearchProfile vanguardAssociative() {
        return new ZebraSearchProfile(22, 7, 620, 60, true, true, 2, 0);
    }

    public static ZebraSearchProfile vanguardTail() {
        return new ZebraSearchProfile(22, 7, 620, 60, true, true, 1, 4);
    }

    public static ZebraSearchProfile vanguard() {
        return new ZebraSearchProfile(22, 7, 620, 60, true, true, 2, 4);
    }

    public static ZebraSearchProfile ponder() {
        return new ZebraSearchProfile(24, 7, 620, 60, true, true, 2, 4);
    }

    int probCutShallowDepth(int depth) {
        if (depth >= 15) {
            return 5;
        }
        if (depth >= 11) {
            return 4;
        }
        return 3;
    }

    int probCutMargin(int depth) {
        return probCutBaseMargin + depth * probCutDepthMargin;
    }
}
