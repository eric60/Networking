package com.company.Project1B;

public class Peer {
    public Integer peerId;
    public String IP;
    public Integer Port;

    public Peer(String ip, Integer port, int id) {
        IP = ip;
        Port = port;
        peerId = id;
    }
}
