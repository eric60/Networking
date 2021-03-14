package com.company.Project2B;

import java.io.*;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 *  This assignment asks you to pretend that TCP does not exist and implement reliable transport yourself on top of UDP.
 *  Your goal is to write (possibly identical) UDP-based sender and receiver chat client programs that communicate through a chat server we maintain
 */
public class ChatClientSender2B {

    /**
     *  Notes:
     * 1) Handle packet loss:                                         Timer just retransmit
     * 2) Handle delay, reordering, duplicate datagram:               Timer, just retransmit. Need Seq number know if duplicate due to delay
     * 3) Handle corrupt packets:                                     1 byte changed, checksum in header, add bytes data and checksum(which is 1's complement of data)
     *                                                               if not equal to 1 then data corrupt, send negative ack 0
     *
     * Don't forget that the channel can reorder packets because of delays, as the delays have some variability.
     * Design own header using checksums, sequence numbers, timers, and any other mechanisms you deem needed
    */

    private static final int        MAX_MSG_SIZE        = 2048; // TCP Counting seq number bytes vs. GBN is counting segments in window size
    private static final String     SERVER              = "plum.cs.umass.edu";
    private static final int        PORT                = 8888;
    private static DatagramSocket   udpsock             = null;
    private static String           datagramIP;
    private static int              datagramPort;

    private static boolean          connEstablished; // name,connRecv,1Syn,2synack,3synack2,4end
    private static int              headerLen           = 18;
    private static int              fileLen;
    private static int              maxTimeouts = 0;
    private static boolean          datagramACKED;
    private static boolean          finSeq;
    private static boolean          thirdFinAck;
    private static boolean          fileOk;
    private static final int        TIMEOUT                = 200; // timeout should be about twice the sample RTT
    private static final int        SLEEP_TIME             = 1;

    /** Data structures and Timers **/
    private static HashMap<Integer, DatagramPacket>     datagramHashMap = new HashMap<>();
    private static ArrayList<Integer>                   datagramOffsets = new ArrayList<>();
    private static byte[]                               byteFile;

    private static Timer                                timer1 = new Timer();
    private static TimerTask                            setNameTask, connReceiverTask, firstSynTask, thirdSynAckTask, datagramTask;
    private static ArrayList<Integer>                   alreadyRecvAcks = new ArrayList<>();
    private static String                               remoteFilePathName = null;



    public static class UDPReader extends Thread {

        public void run() {
            boolean runOnce1 = false, runOnce2 = false;


            while(true) {
                byte[] msg = new byte[MAX_MSG_SIZE];
                DatagramPacket recvDgram = new DatagramPacket(msg, msg.length);

                try {
                    udpsock.receive(recvDgram);
                    String recvStr = new String(recvDgram.getData());
                    System.out.println(recvStr);

                    if(recvStr.contains("OK Hello ES01")) {
                        setNameTask.cancel();
//                       sendDgrm("CHNL LOSS 0");
//                       sendDgrm("CHNL DELAY 0.7");
//                        sendDgrm("CHNL CORRUPTION 0");
                        connReceiverTask = new DatagramTimerTask("CONN ES02");
                        timer1.schedule(connReceiverTask, 0, TIMEOUT);
                    } else if(recvStr.contains("OK Relaying to ES02")) {
                        connReceiverTask.cancel();
                        firstSynTask = new DatagramTimerTask("1.SYN");
                        timer1.schedule(firstSynTask, 0, TIMEOUT);
                    } else if(recvStr.contains("2.SYNACK") && !runOnce1) {
                        firstSynTask.cancel();
                        runOnce1 = true;
                        thirdSynAckTask = new DatagramTimerTask("3.SYNACK2");
                        timer1.schedule(thirdSynAckTask, 0, TIMEOUT);
                    } else if(recvStr.contains("4.END")) {
                        thirdSynAckTask.cancel();
                        connEstablished = true;
                        System.out.println("3 Way handshake over. Connection established. Now sending file data");
                    }
                    else if(finSeq) {
                        if(recvStr.contains("3.FINACK") && !runOnce2) {
                            datagramACKED = true;
                            datagramTask.cancel();
                            thirdFinAck = true;
                            runOnce2 = true;
                        } else {
                            int ackNum = parseACK(recvDgram.getData());
                            System.out.println("ack" + ackNum);
                            if(ackNum == 22) {
                                datagramACKED = true;
                                datagramTask.cancel(); // cancel the last timer sending the filepathname
                                fileOk = true;
                            }
                        }
                    }
                    else if(connEstablished) {
                        int ackNum = parseACK(recvDgram.getData());
                        System.out.println("ack" + ackNum);

                        // sender ignores duplicate ACKS, waits to send next packet until receive next in line ACK
                        if(ackNum == -1) {
                            System.out.println("Got receiver ACK-1 all file data was received. \nSending 2.FINACK on timer. Wait for 3.FINACK before sending filename");
                            datagramACKED = true;
                            datagramTask.cancel();
                            finSeq = true;
                        } else if(!alreadyRecvAcks.contains(ackNum)) {
                            alreadyRecvAcks.add(ackNum);
                            System.out.println("Received non duplicate ACK " + ackNum);

                            if(!datagramOffsets.isEmpty()) {
                                if(ackNum == datagramOffsets.get(0)) { // handle corrupted ACK, only send next seq when ACK20 equal to next seq offset to send
                                    datagramACKED = true;
                                    datagramTask.cancel();
                                }
                            } else {
                                System.out.println("\n\nDatagrams to send list now empty");
                            }
                        } else {
                            System.out.println("Received duplicate ACK " + ackNum + " Ignoring duplicate ACK."); // ignore duplicate ack because getting ack20 means seq0 was correct
                        }
                    }
                } catch(IOException e) {
                    System.out.println(e);
                    continue;
                }
            }
        }
    }

    /**
     *
     * 1) Add header to datagram byte array
     * 2) Add data
     * 3) Compute checksum after all header and file bytes added
     */
    public static DatagramPacket createDatagram(int offset, int byteLen, boolean lastDatagram) throws Exception {
        byte[] msg;
        int len;
        if(lastDatagram) {
            len = headerLen + remoteFilePathName.length();
            System.out.println("Created filename datagram with length " +len);
            msg = new byte[len];
        } else {
            msg = new byte[MAX_MSG_SIZE];
        }
            /** 1) Adding Header
             *  line 0: checksum int,
             *  line 1: seq num aka offset
             *  line 2: file length
             *  line 3: dataLen
             *
             *  index [header-2/16] = ack flag
             *  index [header-1/17] = finish flag
             **/

            msg = addIntToByteArray(msg, offset, 1);
            if(lastDatagram) {
                int strlen = remoteFilePathName.length();
                msg = addIntToByteArray(msg, strlen,2);
            } else {
                msg = addIntToByteArray(msg, fileLen, 2);
            }

            msg = addIntToByteArray(msg, byteLen, 3);

//            msg[headerLen - 2] = 'n'; // Ack Flag
            if(lastDatagram) {
                msg[headerLen - 1] = 'y'; // Finish flag
            } else {
                msg[headerLen - 1] = 'n';
            }

            /** 2) Adding File Byte Data or final remoteFilePath string **/
            if(!lastDatagram) {
                msg = addDataToHeader(msg, offset, byteLen);
            } else {
                msg = addStringToHeader(msg, remoteFilePathName);
            }


            /** 3) Compute checksum from header and data combined  **/
            int checkSum = createCheckSum(msg);
            msg = addIntToByteArray(msg, checkSum, 0);
            System.out.println("\nCHECKSUM: " + checkSum);

            if(lastDatagram) {
                System.out.println("Sent last filename datagram with length " + msg.length);
                DatagramPacket sendDgram = new DatagramPacket(msg, msg.length, InetAddress.getByName(datagramIP), datagramPort);
                return sendDgram;
            }

             /** 4) Create Datagram from finished byte array data  **/
            DatagramPacket sendDgram = new DatagramPacket(msg, MAX_MSG_SIZE, InetAddress.getByName(datagramIP), datagramPort);
            return sendDgram;
    }

    public static byte[] addIntToByteArray(byte[] datagramArr, int toAddInt, int line) {
            byte[] intByteArr = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(toAddInt).array();
            for(int i = 0; i < 4; i++) {
                int j = i + (line * 4);
                datagramArr[j] = intByteArr[i];
            }
            return datagramArr;
    }

    public static byte[] addDataToHeader(byte[] message, int offset, int byteLen) {
        // ex. headerLen = 3, [012]3
        for(int i = 0; i < byteLen; i++) {
            message[i + headerLen] = byteFile[i + offset];
            /** Inputting file data in datagram starts at index 18, message[18] = byteFile[0] up to message[20] = bytefile[2] if len = 3 **/
        }
        return message;
    }

    public static byte[] addStringToHeader(byte[] msg, String msgstr) {
        byte[] strBytes = msgstr.getBytes();
        for(int i = 0; i < strBytes.length; i++) {
            msg[i + headerLen] = strBytes[i];
        }
        return msg;

    }

    // create checksum based on the byte data you are sending from the overall bytefile we made
    public static int createCheckSum(byte[] buffer) {
        byte[] bufCopy = Arrays.copyOf(buffer, buffer.length);
        int length = bufCopy.length - 4;
        int i = 4; // skip first checksum field

        int sum = 0;
        int data;

        while (length > 1) {
            data = (((bufCopy[i] << 8) & 0xFF00) | ((bufCopy[i + 1]) & 0xFF));
            sum += data;
            // 1's complement carry bit correction in 16-bits (detecting sign extension)
            if ((sum & 0xFFFF0000) > 0) {
                sum = sum & 0xFFFF;
                sum += 1;
            }

            i += 2;
            length -= 2;
        }

        // Handle bytes odd length buffers
        if (length > 0) {
            sum += (bufCopy[i] << 8 & 0xFF00);

            if ((sum & 0xFFFF0000) > 0) {
                sum = sum & 0xFFFF;
                sum += 1;
            }
        }

        sum = ~sum;
        sum = sum & 0xFFFF;
        return sum;
    }


     /**
      * Datagram = HEADER + DATA
      * create header
      * addDataToHeader
      *
      * This method is doing the math of splitting up the file byte array into datagrams to send with a set header len and variable data len
      */
    public static void createDatagramHashMap() throws Exception {
        int totalFileLen = byteFile.length;
        int datagramLimit = MAX_MSG_SIZE - headerLen;
        int remainingFileToSend = totalFileLen;

        int offset = 0 - datagramLimit; // 1) need first packet offset to be 0

        while(true) {
            if(remainingFileToSend - datagramLimit <= 0) {
                break;
            }
            offset += datagramLimit; // 2) byte offset of file byte array
            remainingFileToSend -= datagramLimit;

            DatagramPacket finalDgrmToSend = createDatagram(offset, datagramLimit, false);
            datagramHashMap.put(offset, finalDgrmToSend);
            datagramOffsets.add(offset); // offsets 0, 20, 40
        }
        // Create last packet set FIN flag to 1
        offset += datagramLimit;
        DatagramPacket finalDgrmToSend = createDatagram(offset, remainingFileToSend, false);
        datagramHashMap.put(offset, finalDgrmToSend);
        datagramOffsets.add(offset); // index 0 offset 0, index 1 offset 20, index 2 offset 40
    }


    public static int parseACK(byte[] msg) {
        /*if(msg[4] != 0 && msg[5] != 0) {
            return -10;
        }*/
         byte[] checkSumBytes = Arrays.copyOfRange(msg, 0, 4);
         int ackNum = new BigInteger(checkSumBytes).intValue();
         return ackNum;
    }

    public static void sendDgrm(String input) {
        try {
            DatagramPacket sendDgram = new DatagramPacket((input + "\n").getBytes(), Math.min(input.length() + 1, MAX_MSG_SIZE), InetAddress.getByName(datagramIP), datagramPort);
            udpsock.send(sendDgram);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    public static void sendDgrm(DatagramPacket dp) {
        try {
            udpsock.send(dp);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *  Start Timer for each packet when sent
     *  DatagramTimerTask just resends the datagram if there was no ACK from the receiver
     *
     */
    private static class DatagramTimerTask extends TimerTask {
        private  DatagramPacket  dp;
        private  String         str1;
        private  boolean         lastFileTimer;

        public DatagramTimerTask(DatagramPacket dp, boolean lastFileTimer) {
            this.dp = dp;
            this.lastFileTimer = lastFileTimer;
        }
        public DatagramTimerTask(String strToSend) {
            this.str1 = strToSend;
        }

        public int i = 0;

        public void run() {
            ++i;
            if(lastFileTimer) {
                if(i > (2 * maxTimeouts)) {
                    System.out.println("Closing last timer, curr timeouts 2*maxtimeouts. Receiver must have gotten filename OK. maxtimeouts, curr timeouts: " + maxTimeouts + "," + i );
                    datagramTask.cancel();
                    fileOk = true;
                }
            }
            if(dp != null) {
                sendDgrm(dp);
                System.out.println("Timeout. Resent file datagram from hashmap number" + i);
            }
            if(str1 != null) {
                sendDgrm(str1);
            }
            if(!lastFileTimer && i > maxTimeouts) {
                maxTimeouts = i;
                System.out.println("New maxtimeout " + maxTimeouts);
            }
        }
    }

    public static void establish3WayHandshake() {
        setNameTask = new DatagramTimerTask("NAME ES01");
        timer1.schedule(setNameTask, 0, TIMEOUT);
    }


    public static void main(String[] args) throws Exception {
//        java ChatClientSender -s server_name -p port_number -t filename1 filename2
//        -s plum.cs.umass.edu -p 8888 -t /Users/ericshi/Downloads/TestPa3.txt /Users/ericshi/Downloads/Result.txt
//        -s plum.cs.umass.edu -p 8888 -t /Users/ericshi/Downloads/testimg.jpg /Users/ericshi/Downloads/Result.jpg
       // -s plum.cs.umass.edu -p 8888 -t /Users/ericshi/Downloads/testvid1.mp4 /Users/ericshi/Downloads/resultvid1.mp4

        datagramIP = (args.length > 1 ? args[1] : SERVER);
        datagramPort = (args.length > 2 ? Integer.valueOf(args[3]) : PORT);
        String localFile = (args.length > 3 ? args[5] : "/Users/ericshi/Downloads/TestPa3.txt");;
        remoteFilePathName = (args.length > 3 ? args[6] : "/Users/ericshi/Downloads/Result.txt");


        udpsock = new DatagramSocket();
        Thread udpReader = new UDPReader();
        udpReader.start();

        final long startTime = System.currentTimeMillis();
        /** -----------------------------------------------------------------------------1) First Establish 3 way handshake connection to ES02 **/
        establish3WayHandshake();
        while(!connEstablished) {
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
            }
        }

        /** -----------------------------------------------------------------------------2) Start sending file bytes in datagrams to receiver via server **/
        Path path = Paths.get(localFile);
        byteFile = Files.readAllBytes(path);
        fileLen = byteFile.length;
        System.out.println("file length is: " + fileLen);

        try {
            createDatagramHashMap();
        } catch(Exception e) {
            e.printStackTrace();
        }

        System.out.println("Number of datagrams in datagramHashmap: " + datagramHashMap.size());
        for(int i : datagramOffsets) {
            System.out.println("datagram offset" + i);
        }

        /** ------------------------------------------------------------------------------------------------------------------------------------**/
        int idx = 0;
        while(datagramOffsets.size() != 0) {
            int currOffsetKey = datagramOffsets.get(idx);
            System.out.println("\nIndex: " + idx + " Current offset key: " + currOffsetKey);

            DatagramPacket datagramToSend = datagramHashMap.get(currOffsetKey);
            datagramOffsets.remove(idx);
            datagramTask = new DatagramTimerTask(datagramToSend, false);

            final long startTime2 = System.currentTimeMillis();
            timer1.schedule(datagramTask, 0, TIMEOUT);

            /** STOP AND WAIT UNTIL RECEIVE ACK TO SEND NEXT DATAGRAM **/
            while(!datagramACKED) {
                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException e) {
                }
            }
            datagramACKED = false;
            final long endTime2 = System.currentTimeMillis();
            System.out.println("RTT in ms: " + (endTime2 - startTime2));
        }

        /** ------------------------------------------------------------------------------------------------------------------------------------**/
        System.out.println("Sending 2.FINACK");
        // Wait for 3.FINACK before sending file name, indicating receiver ready to accept filename
        datagramTask = new DatagramTimerTask("2.FINACK");
        timer1.schedule(datagramTask, 0, TIMEOUT);
        while(!datagramACKED || thirdFinAck == false) {
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
            }
        }
        datagramACKED = false;

        /** ------------------------------------------------------------------------------------------------------------------------------------**/
        System.out.println("\n\nGot 3.FINACK. FIN Sequence complete. Receiver ready to accept filename. Sending filename");
        DatagramPacket fileDatagram = createDatagram(0,0,true);
        datagramTask = new DatagramTimerTask(fileDatagram, true);
        timer1.schedule(datagramTask, 0, TIMEOUT);


        while(!fileOk) {
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
            }
        }
        sendDgrm(".");
        sendDgrm("QUIT");
        udpReader.interrupt();

        final long endTime = System.currentTimeMillis();
        System.out.println("Total execution time seconds: " + (endTime - startTime)/1000);

        System.exit(0);

    }

}


