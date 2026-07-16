package com.zawa.client.util;

public class OthelloClientStatus extends AbstractOthelloClientStatus {
    private Integer[][] othello_array={
            {0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0},
            {0,0,0,1,-1,0,0,0},
            {0,0,0,-1,1,0,0,0},
            {0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,0},
            {0,0,0,0,0,0,0,1}
    };

    private Integer turn = 0;

    private Integer myturn = 0;

    private String mynickname = "MyClient";

    private String vsnickname = "VSClient";

    public OthelloClientStatus() {
    }

    @Override
    public Integer[][] get() {
        return this.othello_array;
    }

    @Override
    public Integer getTurn() {
        return this.turn;
    }

    @Override
    public Integer getMyTurn() {
        return this.myturn;
    }

    String MyTurnToString() {
        if (this.myturn == 1) return "Black";
        else if (this.myturn == -1) return "White";
        else return "None";
    }

    String VSTurnToString() {
        if (this.myturn == -1) return "Black";
        else if (this.myturn == 1) return "White";
        else return "None";
    }

    @Override
    public String getNickname() {
        return this.mynickname;
    }

    @Override
    public String getVSNickname() {
        return this.vsnickname;
    }

    @Override
    public void update(Integer[][] array) {
        this.othello_array = array;
    }

    @Override
    public void updateTurn(Integer turn) {
        this.turn = turn;
    }

    @Override
    public void updateMyTurn(Integer myturn) {
        this.myturn = myturn;
    }

    @Override
    public void updateNickname(String nickname) {
        this.mynickname = nickname;
    }

    @Override
    public void updateVSNickname(String vsnickname) {
        this.vsnickname = vsnickname;
    }

    @Override
    public String print() {
        int blackCount = 0;
        int whiteCount = 0;
        for (Integer[] row : othello_array) {
            for (Integer cell : row) {
                if (cell != null) {
                    if (cell == 1) blackCount++;
                    else if (cell == -1) whiteCount++;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s(%s) %d : %s(%s) %d   Username:%s\n",
                this.mynickname, MyTurnToString(), blackCount,this.vsnickname,VSTurnToString(), whiteCount, this.mynickname));
        String border = "＋－＋－＋－＋－＋－＋－＋－＋－＋\n";
        sb.append(border);
        for (Integer[] row : othello_array) {
            sb.append("｜");
            for (Integer cell : row) {
                String piece = " ";
                if (cell != null) {
                    if (cell == 1) piece = "⚫︎";
                    else if (cell == -1) piece = "○";
                }
                sb.append(piece).append("｜");
            }
            sb.append("\n").append(border);
        }
        String currentTurnColor = "未定";
        if (this.turn != null) {
            if (this.turn == 1) currentTurnColor = "Black";
            else if (this.turn == -1) currentTurnColor = "White";
        }
        sb.append("TURN: ").append(currentTurnColor).append("\n");
        return sb.toString();
    }
}
