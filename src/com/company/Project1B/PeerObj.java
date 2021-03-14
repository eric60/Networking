package com.company.Project1B;

/**
 *  New shared PeerObj obj created for each 2 Peers from UDP message
 *  Each new thread has new TCP connection to the Peer connection
 **/
public class PeerObj {
    public Integer NUM_BLOCKS;
    public Integer FILE_SIZE;
    public String IP1;
    public Integer PORT1;
    public String IP2;
    public Integer PORT2;
}
