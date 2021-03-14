package com.company.Project1B;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.company.Project1B.Parser.computeXORChecksum;

public class TorrentClient1B {

    protected static List<Peer> peerList;
    protected static Stack<Integer> availableBlocks;
    protected static HashMap<Integer, ArrayList<Thread>> peerThreadList; // K: PeerId, V:List of Peer's threads
    protected static int numUDP = 1;
    private static AllPeerStats peerStats = new AllPeerStats();

    public TorrentClient1B() {
        peerList = new ArrayList<>();
        peerThreadList = new HashMap<Integer, ArrayList<Thread>>();
        availableBlocks = new Stack<>();
    }

    public static void main(String[] args) throws Exception {
        Socket gradingSocket = new Socket("date.cs.umass.edu", 20001);
        DataOutputStream outToGradingServer = new DataOutputStream(gradingSocket.getOutputStream());
        DataInputStream inFromGradingServer = new DataInputStream(gradingSocket.getInputStream());

        try {
            readGradingServerResponses(outToGradingServer, inFromGradingServer);
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        finally {
            inFromGradingServer.close();
        }
    }

    private static void readGradingServerResponses(DataOutputStream outToGradingServer, DataInputStream inFromGradingServer) throws Exception{
        String query = "IAM 30285758\n";
        byte[] queryBuf = query.getBytes();
        outToGradingServer.write(queryBuf);

        byte[] grading1stResponse = new byte[100000];
        inFromGradingServer.read(grading1stResponse);
        System.out.println(new String(grading1stResponse, "UTF-8"));

        Handler.startPeerDownloads(peerStats);

        System.out.println("---> Sent checksum to Grading Server");
        byte[] checksum = computeXORChecksum(peerStats.allBuffer);
        outToGradingServer.write(checksum);

        // RECEIVE Grading Server 2nd Response
        byte[] grading2ndResponse = new byte[100000];
        inFromGradingServer.read(grading2ndResponse);
        System.out.println(new String(grading2ndResponse, "UTF-8"));
    }

}