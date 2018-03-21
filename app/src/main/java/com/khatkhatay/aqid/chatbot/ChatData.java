package com.khatkhatay.aqid.chatbot;


public class ChatData {

    public String body;
    public boolean isMine;// Did I send the message.

    public ChatData(String Sender, String Receiver, String messageString, boolean isMINE) {
        body = messageString;
        isMine = isMINE;
    }
}