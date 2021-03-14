package com.company.Project1B;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static com.company.Project1B.Parser.parseBlock;
import static com.company.Project1B.TorrentClient1B.*;

public class Handler {

    public static void handleSinglePeer(Socket connectionSocket, AllPeerStats peerStats,
                                        int blockNum) throws Exception {
        DataOutputStream outToPeerServer = new DataOutputStream(connectionSocket.getOutputStream());
        DataInputStream inFromPeerServer = new DataInputStream(connectionSocket.getInputStream());


//        PeerObj obj = sendUDP(peerStats);
//        Peer p1 = new Peer(obj.IP1, obj.PORT1, numUDP);
//        numUDP++;
//        System.out.println(numUDP);
//        Peer p2 = new Peer(obj.IP2, obj.PORT2, numUDP);
//        numUDP++;
//        peerList.add(p1);
//        peerList.add(p2);
//        peerThreadList.put(p1.peerId, new ArrayList<Thread>() );
//        peerThreadList.put(p2.peerId, new ArrayList<Thread>() );

        // GET 30285758_Redsox.jpg:0
        // GET Redsox.jpg:0
        String peerQuery = "GET 30285758_Redsox.jpg:" + blockNum + "\n";
        byte[] queryBuf = peerQuery.getBytes();
        outToPeerServer.write(queryBuf);
        System.out.println("Wrote to peerserver: " + peerQuery);
        byte[] buf1 = new byte[100000];


        while(true) {
            int numBytesHeader = 0;
            byte currByte;
            byte prevByte = -1;

            // Reading each separate block - should be 10,10 each time
            // about 61 bytes header. [0->60], 61
            while ((currByte = inFromPeerServer.readByte()) != -1) {
                if (currByte == 10 && prevByte == 10) {
                    buf1[numBytesHeader++] = currByte;
                    break;
                }
                prevByte = currByte;
                buf1[numBytesHeader++] = currByte;
            }
//                System.out.println("prevByte, currByte: " + prevByte + "," + currByte);
//                System.out.println("Number bytes header is: " + numBytesHeader);

            int byte_offset = parseBlock(buf1)[0];
            int block_length = parseBlock(buf1)[1];

            /** ------------------adding block buffer to the All Buffer. Check all values being added correctly -------------------------- **/
            byte[] singleBlockBuffer = new byte[block_length];
            try {
                inFromPeerServer.readFully(singleBlockBuffer, 0, block_length); // BYTE OFFSET is for the allBuffer not this buffer we're reading into
                peerStats.addBufferToAllBuffer(byte_offset, block_length, singleBlockBuffer);
            } catch(Exception e) {
                throw new IOException("Throwing exception to singlePeerHandler to deal with");
            }
            break;
        }
    }

    /*
     *  All threads with ex.(p1,p2) and (p3,p4) share the same PeerObj obj
     *  Need update NUM_BLOCKS, NumPeers,
     *  strategy for each peer p1,p2,p3,p4 to get number of blocks: getNumBlocksEachPeer
     *  p1consumed, p2consumed
     */
    protected static void startPeerDownloads(AllPeerStats peerStats) throws Exception {
        PeerObj obj = sendUDP(peerStats);
        Peer p1 = new Peer(obj.IP1, obj.PORT1, numUDP);
        numUDP++;
        System.out.println(numUDP);
        Peer p2 = new Peer(obj.IP2, obj.PORT2, numUDP);
        numUDP++;

        peerList.add(p1);
        peerList.add(p2);
        peerThreadList.put(p1.peerId, new ArrayList<Thread>() );
        peerThreadList.put(p2.peerId, new ArrayList<Thread>() );

        for(int i = 0; i < peerStats.getNUM_BLOCKS(); i++) {
            availableBlocks.push(i);
        }

        while(!availableBlocks.isEmpty()) {
            boolean hasOpenThread = false;

            for(Peer p : peerList) {
                int peerKey = p.peerId;
                ArrayList<Thread> list = peerThreadList.get(peerKey);
                if(list.size() < 5) {
                    int blockNum = availableBlocks.pop();
                    System.out.println("ON Socket" + p.IP + ',' + p.Port);
                    try {
                        Socket peerSocket = new Socket(p.IP, p.Port);
                        Thread t = new Thread(new SinglePeerHandler(peerSocket, peerStats, blockNum));
                        list.add(t);
                        hasOpenThread = true;
                        t.start();
                        break;
                    } catch(Exception e) {
                        System.out.println("Peer Server TCP connection refused/dropped you. Removing current Peer from Peer List. Adding Block back to available blocks ");
                        peerList.remove(p);
                        availableBlocks.push(blockNum);
                        break;
                    }
                }
            }
            // No Peers Open
            if(!hasOpenThread) {
                Peer p = peerList.get(0);
                int peerKey = p.peerId;
                List<Thread> list = peerThreadList.get(peerKey);
                if(!list.isEmpty()) {
                    Thread t = list.get(0);
                    // If t is a Thread object whose thread is currently executing, then t.join() will make sure that t is terminated before the next instruction is executed by the program
                    t.join();
                    list.remove(0);
                }
            }
        }

        // wait for all threads finish
        for(Peer p : peerList) {
            for(Thread t : peerThreadList.get(p.peerId)) {
                t.join();
            }
        }
        System.out.println("Exited while loop - no more empty blocks");
        System.out.println("Num blocks read, num total in file: " + peerStats.cntTotalBytesAdded + "," + peerStats.allBuffer.length);
        String path = System.getProperty("user.home") + File.separator + "Downloads" + File.separator + "redsox.jpg";
        System.out.println(path);


       /* try {
            InputStream bis = new ByteArrayInputStream(peerStats.allBuffer);
            BufferedImage image = ImageIO.read(bis);
            ImageIO.write(image, "jpg", new File(path));
        } catch(IOException e) {
            e.printStackTrace();
        }*/
    }


    /*
     *  The total number of open connections your client can use (across all peers)
     *  will be limited to about 10 or so based on your IP address,
     *  so be careful about remembering to close idle connections.
     *
     *  1. The torrent server will not always respond if you query it too fast
     *      but will respond if you query again after a few seconds.
     *      Remember that with UDP, even if the server responds,
     *      the network can drop the datagram.
     *  2. So if you write code like where you necessarily expect a UDP response
     *      for every single one of your requests, your code will seem "stuck"
     *      because a datagram read is a blocking call.
     *  3. You can slow down or, better still, use multiple threads
     */
    private static PeerObj sendUDP(AllPeerStats peerStats) throws Exception {
        final int SIZE = 1024;
        // grading torrent metadata: date.cs.umass.edu, 19876
        // testing torrent metadata: plum.cs.umass.edu  19876
        InetAddress ip = InetAddress.getByName("date.cs.umass.edu");
        Integer port = 19876;

        // GET Redsox.jpg.torrent
        String testMessage = "GET 30285758_Redsox.jpg.torrent\n";

        byte[] sendData = testMessage.getBytes();
        byte[] receiveData = new byte[SIZE];

        DatagramSocket clientSocket = new DatagramSocket();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, port);
        clientSocket.send(sendPacket);
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

        try {
            // datagram read is a blocking call
            clientSocket.receive(receivePacket);
            System.out.println("Received UDP Packet containing 2 new peers");
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            clientSocket.close();
        }


        PeerObj parsedPeerObj = Parser.parseMessage(receivePacket.getData());

        int NUM_BLOCKS = parsedPeerObj.NUM_BLOCKS;
        int FILE_SIZE = parsedPeerObj.FILE_SIZE;

        // all you need is num blocks and file size
        if(peerStats.getNUM_BLOCKS() == 0) {
            peerStats.setByteSizeFileBuf(FILE_SIZE);
            peerStats.setNUM_BLOCKS(NUM_BLOCKS);
        }
        System.out.println("-----The peerStats num blocks is: " + peerStats.getNUM_BLOCKS());
        System.out.println("-----The peerStats byte array  size is: " + peerStats.allBuffer.length);

        return parsedPeerObj;
    }
}
