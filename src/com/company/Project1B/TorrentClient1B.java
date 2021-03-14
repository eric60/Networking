package com.company.Project1B;

import java.io.*;
import java.net.*;
import java.util.*;

public class TorrentClient1B {
    private static List<Peer> peerList;
    private static Stack<Integer> availableBlocks;
    private static HashMap<Integer, ArrayList<Thread>> peerThreadList; // K: PeerId, V:List of Peer's threads
    private int numUDP = 1;

    public TorrentClient1B() {
        peerList = new ArrayList<>();
        peerThreadList = new HashMap<Integer, ArrayList<Thread>>();
        availableBlocks = new Stack<>();
    }

    private class singlePeerHandler implements Runnable {
        private final Socket connectionSocket;
        private final AllPeerStats peerStats;
        private final int blockNum;

        public singlePeerHandler(Socket connectionSocket, AllPeerStats peerStats, int blockNum) {
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

    private void handleSinglePeer(Socket connectionSocket, AllPeerStats peerStats, int blockNum) throws Exception {
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
    private void startPeerDownloads(AllPeerStats peerStats) throws Exception {
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
                            Thread t = new Thread(new singlePeerHandler(peerSocket, peerStats, blockNum));
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
    private PeerObj sendUDP(AllPeerStats peerStats) throws Exception {
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


        PeerObj parsedPeerObj = parseMessage(receivePacket.getData());

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



    public PeerObj parseMessage(byte[] input) throws Exception {
       String str = new String(input, "UTF-8");

        PeerObj peerObj = new PeerObj();

        Scanner scanner = new Scanner(str);
        String numBlocksStr = scanner.nextLine().trim();
        Integer NUM_BLOCKS = Integer.parseInt(numBlocksStr.substring(12));
        peerObj.NUM_BLOCKS = NUM_BLOCKS;

        String fileSizeStr = scanner.nextLine().trim();
        Integer FILE_SIZE = Integer.parseInt(fileSizeStr.substring(11));
        peerObj.FILE_SIZE = FILE_SIZE;

        String Ip1Str = scanner.nextLine().trim();
        int idx1 = Ip1Str.indexOf('/') + 1;
        String IP1 = Ip1Str.substring(idx1);
        peerObj.IP1 = IP1;

        String port1Str = scanner.nextLine().trim();
        Integer PORT1 = Integer.parseInt(port1Str.substring(7));
        peerObj.PORT1 = PORT1;

        // -------------------------------------------------
        String Ip2Str = scanner.nextLine().trim();
        int idx2 = Ip2Str.indexOf('/') + 1;
        String IP2 = Ip2Str.substring(idx2);
        peerObj.IP2 = IP2;

        String port2Str = scanner.nextLine().trim();
        Integer PORT2 = Integer.parseInt(port2Str.substring(7));
        peerObj.PORT2 = PORT2;

//        System.out.println("In Parse Message Checking parsed UDP correctly");
//        System.out.println(NUM_BLOCKS);
//        System.out.println(FILE_SIZE);
        System.out.println(IP1);
        System.out.println(PORT1);
        System.out.println(IP2);
        System.out.println(PORT2);

        return peerObj;
    }

    public static int[] parseBlock(byte[] input) throws UnsupportedEncodingException {
        String str = new String(input, "UTF-8");

        //System.out.println(str);
        if (!str.contains("200 OK")) {
            System.out.println("Status not okay");
            throw new IllegalArgumentException("Status message not right");
        }

        Scanner scanner = new Scanner(str);
        scanner.nextLine(); // Skip status line

        String strOffset = scanner.nextLine().trim();
        Integer byte_offset = Integer.parseInt(strOffset.substring(26));

        String strLength = scanner.nextLine().trim();
        Integer byte_length = Integer.parseInt(strLength.substring(18));

        return new int[] {byte_offset, byte_length};
    }

    /**
     * Shared variables:
     * 1. allBuffer so every thread can input their block into the all buffer.
     *
     **/
    private static class AllPeerStats {
        private Integer NUM_BLOCKS = 0;
        public byte[] allBuffer = null;
        private int cntTotalBytesAdded = 0;


        private synchronized void setByteSizeFileBuf(int size) {
            if(allBuffer == null) {
                allBuffer = new byte[size];
            }
        }

        private synchronized void addBufferToAllBuffer(int offsetinAllBuffer, int blockLength, byte[] toAddBuf) {
            for(int i = 0; i < blockLength; i++) {
                allBuffer[offsetinAllBuffer + i] = toAddBuf[i];
                cntTotalBytesAdded++;
            }
        }

        private synchronized void setNUM_BLOCKS(int numBlocks) {
            if(NUM_BLOCKS  == 0) {
                this.NUM_BLOCKS = numBlocks;
            }
        }

        private synchronized int getNUM_BLOCKS() {
            return this.NUM_BLOCKS;
        }
    }


    /**
     *  New shared PeerObj obj created for each 2 Peers from UDP message
     *  Each new thread has new TCP connection to the Peer connection
     *
     **/
    private class PeerObj {
        private Integer NUM_BLOCKS;
        private Integer FILE_SIZE;
        public String IP1;
        public Integer PORT1;
        public String IP2;
        public Integer PORT2;
    }

    private class Peer {
        public Integer peerId;
        public String IP;
        public Integer Port;

        public Peer(String ip, Integer port, int id) {
            IP = ip;
            Port = port;
            peerId = id;
        }
    }

    public static byte[] computeXORChecksum(byte[] bytes) {
        if(bytes.length <= 4) {
            return bytes;
        }
        byte[] checksum = new byte[5];
        for(int i = 0; i < 4; i++) {
            checksum[i] = bytes[i];
            for(int j = i + 4; j < bytes.length; j+=4) {
                checksum[i] = (byte) (checksum[i] ^ bytes[j]);
            }
        }
        checksum[4] = (byte) '\n';
        return checksum;
    }


    public static void main(String[] args) throws Exception {
        TorrentClient1B torrentClient = new TorrentClient1B();
        AllPeerStats peerStats = new AllPeerStats();

        Socket gradingSocket = new Socket("date.cs.umass.edu", 20001);
        DataOutputStream outToGradingServer = new DataOutputStream(gradingSocket.getOutputStream());
        DataInputStream inFromGradingServer = new DataInputStream(gradingSocket.getInputStream());

        try {
            String query = "IAM 30285758\n";
            byte[] queryBuf = query.getBytes();
            outToGradingServer.write(queryBuf);

            byte[] grading1stResponse = new byte[100000];
            inFromGradingServer.read(grading1stResponse);
            System.out.println(new String(grading1stResponse, "UTF-8"));

            torrentClient.startPeerDownloads(peerStats);

            System.out.println("---> Sent checksum to Grading Server");
            byte[] checksum = computeXORChecksum(peerStats.allBuffer);
            outToGradingServer.write(checksum);

            // RECEIVE Grading Server 2nd Response
            byte[] grading2ndResponse = new byte[100000];
            inFromGradingServer.read(grading2ndResponse);
            System.out.println(new String(grading2ndResponse, "UTF-8"));
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            inFromGradingServer.close();
        }
    }

}