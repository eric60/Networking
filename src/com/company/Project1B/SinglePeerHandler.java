package com.company.Project1B;

import java.net.Socket;

import static com.company.Project1B.Handler.handleSinglePeer;
import static com.company.Project1B.TorrentClient1B.availableBlocks;


public class SinglePeerHandler implements Runnable {
    protected Socket connectionSocket;
    protected AllPeerStats peerStats;
    protected int blockNum;

    public SinglePeerHandler(Socket connectionSocket, AllPeerStats peerStats, int blockNum) {
        this.connectionSocket = connectionSocket;
        this.peerStats = peerStats;
        this.blockNum = blockNum;
    }

    @Override
    public void run() {
        try {
            handleSinglePeer(this.connectionSocket, this.peerStats, blockNum);
            connectionSocket.close();

        } catch(Exception e) {
            e.printStackTrace();
            // Catch dropped TCP connection or Didn't read all bytes from TCP connection. IOException -> SocketException, EOFException
            availableBlocks.push(blockNum);
            System.out.println("Caught dropped Peer Server TCP connection. Added blockNum back");
        }
    }
}
