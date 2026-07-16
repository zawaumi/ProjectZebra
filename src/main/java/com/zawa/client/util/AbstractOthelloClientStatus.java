package com.zawa.client.util;

public abstract class AbstractOthelloClientStatus implements OthelloClientStatusInterface{
        private Integer[][] othello_array;
        private Integer turn;
        private Integer myturn;
        private String mynickname;
        private String vsnickname;
        public abstract Integer[][] get();
        public abstract Integer getTurn();
        public abstract Integer getMyTurn();
        public abstract String getNickname();
        public abstract String getVSNickname();
        public abstract void update(Integer[][] othello_array);
        public abstract void updateTurn(Integer turn);
        public abstract void updateMyTurn(Integer myturn);
        public abstract void updateNickname(String nickname);
        public abstract void updateVSNickname(String vsnickname);
        public abstract String print();
}