package com.company.Project2B;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class ChatClientReceiver2B {
    private static final int                MAX_MSG_SIZE  = 2048;
    private static final String             SERVER        = "plum.cs.umass.edu";
    private static final int                PORT          = 8888;
    private static DatagramSocket           udpsock       = null;
    private static String                   datagramIP;
    private static int                      datagramPort;
    private static boolean                  nameSet;
    private static boolean                  finAck;

    private static int                      headerLen = 18;
    private static byte[]                   fileBuf = null; // initialized when receive first non corrupt datagram with file length
    private static boolean[]                seqNumArr = null; // returns next ack num, Ack 20 gimme byte 20,
    private static boolean                  fileTransferComplete;
    private static String                   filePathName = null;
    private static int                      filePathLen;



    public static void sendDgrm(String input) {
        try {
            DatagramPacket sendDgram = new DatagramPacket((input + "\n").getBytes(), Math.min(input.length() + 1, MAX_MSG_SIZE), InetAddress.getByName(datagramIP), datagramPort);
            udpsock.send(sendDgram);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    public static void sendDgrm(byte[] message) {
        try {
            DatagramPacket dp = new DatagramPacket(message, message.length, InetAddress.getByName(datagramIP), datagramPort);
            udpsock.send(dp);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // Receives datagram and write to standard output
    public static class UDPReader extends Thread {
        boolean beginAccepptingFile;
        public void run() {

            while(true) {
                byte[] msg = new byte[MAX_MSG_SIZE];
                DatagramPacket recvDgram = new DatagramPacket(msg, msg.length);

                try {
                    udpsock.receive(recvDgram);
                    String recvStr = new String(recvDgram.getData());
                    System.out.println(recvStr);


                    if(recvStr.contains("OK Hello ES02")) {
                        nameSet = true;
                    } else if(!nameSet) {
                        sendDgrm("NAME ES02");
//                        sendDgrm("CHNL LOSS 0");
                    } else if(recvStr.contains("1.SYN")) {
                        // clear previous conn attempt
                        sendDgrm(".");
                        sendDgrm("CONN ES01");
                        sendDgrm("2.SYNACK");
                    } else if(recvStr.contains("3.SYNACK2")) {
                        beginAccepptingFile = true;
                        sendDgrm("4.END");
                    }
                    else if(fileTransferComplete) {
                        int parseNum = parseDatagram(msg);
                        System.out.println("parseNum: " + parseNum);

                        if(recvStr.contains("2.FINACK")) {
                            sendDgrm("3.FINACK"); // Keep sending 3.FINACK until sender gets it. When the sender finally gets it once they will send the filename
                            finAck = true;
                        } else if(!finAck) {
                            int ackNum = -1; // Keep sending ACK-1 until sender gets it. When sender finally gets it they will send 2.FINACK
                            byte[] ackBytes = createAckBytes(ackNum);
                            sendDgrm(ackBytes);
                            System.out.println("Didn't receive 2.FINACK yet. ReSent ACK" + ackNum);
                        } else if(parseNum == 2) {
                            filePathName = parseDatagramtoStr(msg);
                            System.out.println("filePathName not corrupt: " + filePathName);
                            int ackNum = 22;
                            byte[] ackBytes = createAckBytes(ackNum);
                            sendDgrm(ackBytes);
                            System.out.println("Sent final filepathName ACK" + ackNum);
                        } else {
                            System.out.println("File pathname corrupt OR received inflight seq num. No Ack sent. Waiting for retransmission of filename");
                        }
                    }
                    else if(beginAccepptingFile){
                        // parses header and adds file data to recvFile array IFF checksum is ok
                        if(parseDatagram(msg) == 1) {
                            int ackNum = getNextAckNum();
                            byte[] ackBytes = createAckBytes(ackNum);
                            sendDgrm(ackBytes);
                            System.out.println(">>>>>>>Packet not corrupt. Sent ACK" + ackNum);
                        } else {
                            System.out.println(">>>>>>>No ack sent. corrupted packet. waiting for retransmission of that packet");
                        }
                    }
                } catch(IOException e) {
                    System.out.println(e);
                    continue;
                }
            }
        }
    }


    public static byte[] createAckBytes(int ack) {
        byte[] ackBytes = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN).putInt(ack).array();
        ackBytes[4] = 70;
        return ackBytes;
    }
    public static int parseDatagram(byte[] msg) {
            byte[] checkSumBytes = Arrays.copyOfRange(msg, 0, 4); // to exclusive, 0123
            byte[] seqNumBytes = Arrays.copyOfRange(msg, 4, 8);
            byte[] fileLenBytes = Arrays.copyOfRange(msg, 8, 12);
            byte[] dataLenBytes = Arrays.copyOfRange(msg, 12, 16);
            byte finishFlagBytes = msg[headerLen - 1];

            int checkSum = new BigInteger(checkSumBytes).intValue();
            int seqNum = new BigInteger(seqNumBytes).intValue();
            int fileLen = new BigInteger(fileLenBytes).intValue();
            int dataLen = new BigInteger(dataLenBytes).intValue();
            char finishFlag = (char)finishFlagBytes;

            System.out.println("\nCHECKSUM field: " + checkSum);
            System.out.println("seqnum: " + seqNum);
            System.out.println("fileLen: " + fileLen);
            System.out.println("dataLen: " + dataLen);


        /** ----------------------------------------------------**/
            if(!isCheckSumOk(msg, checkSum)) {
                // Don't add to file buffer,
                // 1) Just wait for retransmission for non corrupted packet OR
                // 2) send Ack for the seq number in packet, but could be corrupted?
                return -1;
            }

            /** For parsing last file datagram**/
            if(finishFlag == 'y') {
                filePathLen = fileLen;
                return 2;
            }
            if(fileBuf == null) {
                fileBuf = new byte[fileLen];
                seqNumArr = new boolean[fileLen];
                System.out.println("file buffer size set: " + fileBuf.length);
            }
            addDatagramToFileBuffer(msg, seqNum, dataLen);
            return 1;
    }

    public static String parseDatagramtoStr(byte[] msg) {
        byte[] strbytes = new byte[filePathLen];
        for(int i = 0; i < filePathLen; i++) {
            strbytes[i] = msg[i + headerLen];
        }
        return new String(strbytes);
    }


    public static void addDatagramToFileBuffer(byte[] message, int offset, int dataLen) {
        /** Update seqNumArr to keep track of bytes gotten, need to send first ack needed **/
        for(int i = 0; i < dataLen; i++) {
            fileBuf[i + offset] = message[i + headerLen];
            seqNumArr[i + offset] = true;
        }
    }


    public static boolean isCheckSumOk(byte[] message, int checkSum) {
        int computeDgrmSum = createCheckSum(message);
       // int result = ~(checkSum + computeDgrmSum);
        System.out.println("IN CHECKSUMOK METHOD...result: " + computeDgrmSum);
        if(computeDgrmSum == checkSum) {
            return true;
        }
        return false;
    }


    /**
     *  Returns first offset not received yet.
     *  Ex. Get seq nums 20-59 first due to reordering, send Ack0 for sender to send 0-19
     **/
    public static int getNextAckNum() {
        for(int i = 0; i < seqNumArr.length; i++) {
            if(seqNumArr[i] == false) {
                return i;
            }
        }
        // File transfer complete: if received all seq nums in file, where seqnums[i] all true
        System.out.println("\nFile Transfer Complete. Send 1.FIN or ACK-1 to let sender know to send filepath name");
        fileTransferComplete = true;
        return -1;
    }

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

    public static void main(String[] args) throws IOException {
//        java ChatClientReceiver.java plum.cs.umass.edu, 8888
//        java ChatClientSender -s server_name -p port_number -t filename1 filename2

        datagramIP = (args.length > 1 ? args[1] : SERVER);
        datagramPort = (args.length > 2 ? Integer.valueOf(args[3]) : PORT);
        udpsock = new DatagramSocket();
        Thread udpReader = new UDPReader();
        udpReader.start();

        /** 1) Initial name to server, sender client will CONN to this name **/
        sendDgrm("NAME ES02");

        // 2) After connection established, start accepting bytes into byte file[]
        // 3) Wait final transfer complete
        while(filePathName == null) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
            }
        }

        System.out.println("\n>>>>>File transfer complete and received 2.FINACK");
        // 4) When file transfer complete, save file to receiver machine

        FileOutputStream out = new FileOutputStream(filePathName);
        out.write(fileBuf);
        out.close();
        System.out.println("Saved file: " + filePathName);

        sendDgrm(".");
        sendDgrm("QUIT");
        System.exit(0);

// diff /Users/ericshi/Downloads/Result.txt /Users/ericshi/Downloads/TestPa3.txt
// diff /Users/ericshi/Downloads/Result.jpg /Users/ericshi/Downloads/testimg.jpg
//        diff /Users/ericshi/Downloads/resultvid1.mp4 /Users/ericshi/Downloads/testvid1.mp4
    }

}
