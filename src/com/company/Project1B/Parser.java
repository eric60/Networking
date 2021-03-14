package com.company.Project1B;

import java.io.UnsupportedEncodingException;
import java.util.Scanner;

public class Parser {
    public static PeerObj parseMessage(byte[] input) throws Exception {
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
}
